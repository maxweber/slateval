(ns slateval.conn
  (:require
    [slateval.db :as db #?@(:cljs [:refer [DB FilteredDB]])]
    [extend-clj.core :as extend]
    )
  #?(:clj
     (:import
       [slateval.db DB FilteredDB])))

(extend/deftype-atom Conn [atom]
  (deref-impl [this]
    (:db @atom))
  (compare-and-set-impl [this oldv newv]
    (compare-and-set!
      atom
      (assoc @atom :db oldv)
      (assoc @atom :db newv))))

(defn- make-conn [opts]
  (->Conn (atom opts)))

(defn with
  ([db tx-data] (with db tx-data nil))
  ([db tx-data tx-meta]
   {:pre [(db/db? db)]}
   (when (db/temporal-view? db)
     (throw (ex-info "Cannot transact against an as-of/since/history database value"
                     {:error :transact/temporal-view})))
   (let [q-max-tx (db/q-max-tx db)
         max-tx   (:max-tx db)]
     ;; Check that the storage hasn't been modified since this db snapshot was created.
     ;; q-max-tx is the latest tx in storage, max-tx is what this snapshot sees.
     ;; If q-max-tx > max-tx, storage has been modified.
     (when (pos? (compare q-max-tx max-tx))
       (throw (ex-info "underlying tuple store has already been modified"
                       {:max-tx max-tx
                        :q-max-tx q-max-tx}))))
   (if (instance? FilteredDB db)
     (throw (ex-info "Filtered DB cannot be modified" {:error :transaction/filtered}))
     (db/transact-tx-data (db/->TxReport db db [] {} tx-meta) tx-data))))

(defn with-dry-run
  "Like `with`, but speculative: the transaction is validated and applied to
   the returned :db-after value in memory only — nothing is written to
   storage. Reads on :db-after see the new datoms via its :pending-writes
   overlay. Chaining another dry-run on the returned :db-after keeps the
   previous speculative datoms visible; a real transact against a
   speculative db value throws."
  ([db tx-data] (with-dry-run db tx-data nil))
  ([db tx-data tx-meta]
   {:pre [(db/db? db)]}
   (if (instance? FilteredDB db)
     (throw (ex-info "Filtered DB cannot be modified" {:error :transaction/filtered}))
     (db/transact-tx-data (assoc (db/->TxReport db db [] {} tx-meta)
                                 :slateval.db/dry-run true)
                          tx-data))))

(defn ^DB db-with
  "Applies transaction to an immutable db value, returning new immutable db value. Same as `(:db-after (with db tx-data))`."
  [db tx-data]
  {:pre [(db/db? db)]}
  (:db-after (with db tx-data)))

(defn conn? [conn]
  (and
    #?(:clj  (instance? clojure.lang.IDeref conn)
       :cljs (satisfies? cljs.core/IDeref conn))
    (if-some [db @conn]
      (db/db? db)
      true)))

(defn conn-from-db [db]
  {:pre [(db/db? db)]}
  (make-conn {:db db}))

(defn conn-from-datoms
  ([datoms]
   (conn-from-db (db/init-db datoms nil {})))
  ([datoms schema]
   (conn-from-db (db/init-db datoms schema {})))
  ([datoms schema opts]
   (conn-from-db (db/init-db datoms schema opts))))

(defn create-conn
  ([]
   (conn-from-db (db/empty-db nil {})))
  ([schema]
   (conn-from-db (db/empty-db schema {})))
  ([schema opts]
   (conn-from-db (db/empty-db schema opts))))

(defn ^:no-doc -transact! [conn tx-data tx-meta]
  {:pre [(conn? conn)]}
  (let [*report (volatile! nil)]
    (swap! conn
      (fn [db]
        (let [r (with db tx-data tx-meta)]
          (vreset! *report r)
          (:db-after r))))
    @*report))

(defn transact!
  ([conn tx-data]
   (transact! conn tx-data nil))
  ([conn tx-data tx-meta]
   {:pre [(conn? conn)]}
   (locking conn
     (let [report (-transact! conn tx-data tx-meta)]
       (doseq [[_ callback] (:listeners @(:atom conn))]
         (callback report))
       report))))

(defn reset-schema! [conn schema]
  {:pre [(conn? conn)]}
  (let [db (swap! conn db/with-schema schema)]
    db))

(defn listen!
  ([conn callback]
   (listen! conn (rand) callback))
  ([conn key callback]
   {:pre [(conn? conn)]}
   (swap! (:atom conn) update :listeners assoc key callback)
   key))

(defn unlisten! [conn key]
  {:pre [(conn? conn)]}
  (swap! (:atom conn) update :listeners dissoc key))
