# slateval proof-of-concept for storrito-server

## Context

The storrito-server app (main checkout `/home/max/workspace/storrito2`, branch `trunk` @ `be1d8c783b`) runs on Datomic. An existing worktree `/home/max/workspace/datahike-proof-of-concept` (21 commits on trunk) proves the app can run on Datahike by **shadowing the `datomic.api` namespace**: `com.datomic/peer` is removed from `deps.edn` and a hand-written shim at `src/clj/datomic/api.clj` reimplements the peer API — the ~150 `[datomic.api :as d]` call sites stay untouched. Every `transact` is routed through the Tigris/S3 EventStore so the local DB is a rebuildable projection of the event log.

Goal: create a new worktree from trunk and do the same PoC with **slateval** (`/home/max/workspace/slateval` — the Datascript fork on SlateDB, just upgraded to `io.slatedb/slatedb-uniffi` 0.14.1 from Maven Central). An older `dbval-proof-of-concept` worktree (819 commits stale, call-site-rewrite approach — do NOT rebase it) serves as a reference for slateval-specific gotchas (UUID entity IDs, etc.).

**User decisions**:
- Implement a real `as-of` in the slateval library first (not a stub).
- **No EventStore integration in `datomic.api/transact`** — SlateDB already persists everything to an object store, so the event-sourcing projection layer of the datahike PoC is unnecessary here. `transact` writes directly through slateval; this removes the tempid-replay-determinism problem, the `catch-up!`/optimistic-append machinery, and the `:projection/*` bookkeeping attrs entirely.

### Verified facts that shape the plan

- slateval advantages over Datahike: `:db.fn/cas`/`:db/cas` and `missing?` are built-in; pull tolerates unknown attrs (drop the datahike shim's `filter-pull-pattern`); keyword `entid` works via implicit `:db/ident` schema.
- **`slateval.conn/with` is NOT speculative** (`conn.cljc:22-37`) — it writes the batch to SlateDB, and its `q-max-tx` guard would make the next real transact throw. `d/with` at `storrito/import/background.clj:367` needs a true dry-run mode. Mandatory library work.
- **as-of ≈ `(assoc db :max-tx t)`**: all index reads already filter `(uuid<= (:tx datom) (:max-tx db))` — except **`-index-range` (db.cljc:1255-1275) which is missing that filter** (latent bug, must fix). `com.yetanalytics.squuid/time->uuid` (already a dep) converts Date→upper-bound squuid for Date-based as-of.
- Entity IDs are UUIDs, tx IDs are monotonic squuids (`basis-t` is a UUID, not a number).
- Query patterns max 4 elements; 3 production files use Datomic 5-element history patterns.
- slateval needs **Java 22+** (app Dockerfile is `azul/zulu-openjdk:21.0.4-jdk-crac`; CRaC unused by `bin/dev`; host has JDK 24). Native lib self-extracts; add `--enable-native-access=ALL-UNNAMED`.
- `:local/root` in dev containers: the dbval PoC's `bin/dev` mounts `../dbval` at `/dbval` into the shadow-cljs/prepare/app/repl containers — same trick for `../slateval`.

## Pre-flight

1. Create worktree:
   `git -C /home/max/workspace/storrito2 worktree add -b slateval-proof-of-concept /home/max/workspace/slateval-proof-of-concept trunk`
2. `deps.edn`: remove `com.datomic/peer`; add `slateval/slateval {:local/root "../slateval"}`.
3. `Dockerfile`: `FROM azul/zulu-openjdk:24-jdk`; rebuild app image (local tag written to `docker-image-tags/container` on the branch — avoid pushing).
4. `bin/dev`: mount `../slateval` at `/slateval` (copy the 4 dbval mount sites); add `-J--enable-native-access=ALL-UNNAMED` to the app clojure command; take the datahike branch's changes (no transactor, no control/admin, `init!` mkdirs `db/org-db`). Run blue only.

## Phase A — slateval library work (`/home/max/workspace/slateval`)

- **A1. Dry-run `with`** (blocks the shim): option threaded `conn/with` → `db/transact-tx-data` that skips `(.write conn batch)` but keeps `:pending-writes` on `db-after` so speculative reads see the tx datoms via `pending-keys-in-range`; must not trip the `q-max-tx` guard afterward.
- **A2. `as-of` / `as-of-t`**: `(as-of db t)` accepting tx-squuid or Date/Instant (`time->uuid`); impl `(assoc db :max-tx tx :slateval/as-of tx)`; **fix `-index-range`** to apply the `uuid<=` max-tx filter like the other 4 index methods; `basis-t` stays `(:max-tx db)`.
- **A3. `since` / `since-t`** (small): optional lower-bound tx filter before `datoms-filter` in the index eductions.
- **A4. `history`** (can land after boot milestone): `:slateval/history?` flag that skips `datoms-filter` in the index eductions. Do NOT extend the query engine to 5-element patterns — fix the 3 call sites instead (C7).
- **A5.** No library auto-`:db/txInstant` — the shim stamps it explicitly in the recorded event (replay determinism requires it anyway).
- **A6.** Keep `entid` strict (no uuid-string parsing); shim/boundaries parse.
- **A7.** `script/test_clj.sh` green + tests for A1–A4.

## Phase B — the `datomic.api` shim

New `src/clj/datomic/api.clj` on the PoC branch, ported from the datahike shim (479 lines, at `/home/max/workspace/datahike-proof-of-concept/src/clj/datomic/api.clj`). Key rows (diffs vs datahike):

| fn | slateval impl |
|---|---|
| `q`/`query` | `slateval.core/q` + keep `normalize-query-value` (keyword→entid via `slateval.db/entid`) |
| `pull`/`pull-many` | direct; **drop `filter-pull-pattern`**; parse uuid-string eids |
| `entity`/`touch` | direct (`touch` → `slateval.core/touch`); uuid-string parse |
| `with` | A1 dry-run variant |
| `transact` | **direct** (no EventStore): `(locking conn …)` → `ensure-tx-instant` stamps `{:db/id :db/current-tx :db/txInstant (Date.)}` unless caller provided one → `slateval.core/transact!` → `storrito.db-view.notify/notify!` → return `realized-future` of the report |
| `tempid`/`resolve-tempid` | fresh string / `(get tempids tempid)` |
| `datoms` | direct; `:vaet` → `:avet` with a/v swapped (refs auto-indexed) |
| `as-of`/`since`/`history`/`as-of-t`/`since-t` | Phase A fns |
| `basis-t` | `(:max-tx db)` (squuid — audit numeric consumers, Phase D) |
| `next-t` | return `basis-t` (can't `inc` a uuid) |
| `entid` | uuid → itself; uuid-string → `parse-uuid`; keyword; lookup-ref |
| `attribute` | back with the full harvested Datomic schema kept in a var by `app.datomic.schema` (slateval schema drops non-ref valueType) |
| `connect` | `slateval.core/create-conn` with `{:db-file … :object-store-url …}` |
| `create-database`/`database-exists?`/`delete-database`/`get-database-names` | mkdirs / file-exists / delete-tree / list `/db/org-db` |
| `function`/`log`/`tx-range` | `unsupported` throw (same as datahike) |
| `sync`/`release`/`gc-storage` | no-ops; `realized-future` reused |

## Phase C — app integration (compile → boot → one write → breadth)

- **C1.** Bring over library-agnostic files from the datahike branch via `git checkout datahike-proof-of-concept -- <file>`: `src/clj/storrito/db_view/notify.clj`, `src/clj/app/system/tenant.clj`, `src/clj/app/schema_migration/core.clj` (migrate! gutted), the enum-unwrap/CAS/archive call-site fixes (`storrito/gallery/folder_shared.clj`, `storrito/story_media/{archive,query,unarchive}.clj`, `storrito/story_posting/{prepare,start}.clj`, `storrito/instagram_agent/story_posting.clj`, `storrito/story_upload/save.clj`), rich-comment fixes (`app/query.clj`, `app/rendering{,_template}/gather.clj`), `{basis/src/clj/basis,src/clj/storrito}/datomic/util.clj`, `src/clj/app/log/{core,util}.clj`. Delete `src/clj/app/datomic/tx_report_queue.clj`. Skip the `missing?`-rewrite commit (slateval has it built-in).
- **C2.** `src/clj/app/config.clj`: `:datomic/uri` → `{:db-file (str "/db/org-db/" org-uuid) :object-store-url "file:///"}`.
- **C3.** `src/clj/app/datomic/connection.clj`: take the datahike version (only calls shim fns).
- **C4.** `src/clj/app/datomic/schema.clj`: keep harvesting from `app.schema-migration.core/migrations` (+ `extra-schema-tx` without the `:projection/*` attrs — no projection bookkeeping); normalize for slateval (`:db/valueType` only ref/tuple); `initialize!` = `reset-schema!` then transact missing idents directly via `slateval.core/transact!` (bypassing EventStore); stash the full harvested schema in a var for `d/attribute`; add `:db/index true` for non-ref/non-unique attrs used in `:avet` datoms calls.
- **C5. Compile gate**: `clojure -M:dev -e "(require 'datomic.api 'storrito.main)"` in the prepare container.
- **C6.** `bin/dev` (see Pre-flight 4).
- **C7. slateval-specific fixes** (port from the dbval branch, re-targeted at `datomic.api`): `app/editor2/save_command.clj` (parse-entity-id for string eids, string tempids), `storrito/story_media/get.clj` (`Long/valueOf` → `parse-uuid`), `storrito/gallery_db_view/core2.clj` (numeric eid sort → uuid/string desc compare, on top of the datahike version), `app/subscription/current_state.clj:337-343` (numeric `>=` on basis-t → uuid `compare`), 5-element history patterns → 4 elements in `app/free_plan/trial_status.clj:90`, `app/rendering/cleanup.clj:51`, `storrito/gallery/generate_preview.clj:261`.
- **C8.** `src/clj/app/log/datafy.clj`: Datafiable for `slateval.db.DB` summarizing `{:max-tx :db-file :as-of-t}` instead of dumping datoms.

## Phase D — audit + verification

Audit greps on the worktree: `Long/valueOf|parseLong` (eid parsing), `js/parseInt` in cljs, `basis-t|next-t` (numeric use of t), `d/tempid` (partition-style tempids), `d/datoms .* :avet` (non-indexed attrs), `sort-by`-on-eid assumptions, `?tx` in queries.

Milestones, in order:
1. slateval tests green on JDK 24 after Phase A.
2. Compile gate (C5).
3. Boot: `bin/dev` up, tenant connects, `initialize!` + `bootstrap!` succeed, SlateDB files under `db/org-db/<uuid>`.
4. Browser login; db-views render (read path).
5. Create/save a story in editor2 (direct transact + `:db/txInstant` stamp + `notify!` + gallery view update).
6. Durability: stop and restart the app; the story is still there (SlateDB reopens from `db/org-db/<uuid>` and `q-max-tx` restores the basis).
7. Kaocha suites (`clojure -M:dev:test -m kaocha.runner`), prioritizing story-media/gallery/editor2; triage Datomic-specific failures.

## Open questions (non-blocking)

- Map-form `d/q` `:limit`/`:offset` support in slateval (else strip in `normalize-query-map`).
- Connection close/release on tenant shutdown (slateval "better connection management" TODO).
- Blue/green deploys would share one SlateDB path — single-writer; PoC runs blue only.
