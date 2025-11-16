# dbval

dbval is a fork of [Datascript](https://github.com/tonsky/datascript) and a
proof-of-concept (aka 'do not use it in production') that you can implement a
library that offers Datomic-like semantics on top of a mutable relational
database like Sqlite.

The most important goal is to serve the database as a value, meaning you can get
the current database value and query it as long as you like without that it
changes underneath you. You can also get the database as a value for any point
in the past.

Sqlite was chosen since you have no network-round trip when you read from the
database. Thereby you can do [hundreds of small
queries](https://www.sqlite.org/np1queryprob.html) instead of trying to force
everything into one big SQL statement.

The idea to have something Datomic-like on top of a relational database was
already born in 2013 during a project where we were forced to use Postgres
instead of Datomic. It was a side quest ever since. We tried many different
database schemas. In 2025 a breakthrough were accomplished after learning about
FoundationDB and that [Griffin already has a fork of Datascript that runs on top
of FoundationDB](https://www.juxt.pro/blog/clojure-in-griffin/#foundationdb).

While FoundationDB is an amazing piece of technology it requires a lot of
infrastructure. For us mortals there is basically only the option to use its
Kubernetes operator. Meanwhile the [Rails
community](https://m.youtube.com/watch?v=Sc4FJ0EZTAg) and projects like
[Turso](https://turso.tech/) proofed that it is viable to have one Sqlite
database per (SaaS) customer.

At its core FoundationDB is a transactional ordered key value store. Something
you can mimic in Sqlite with a table like:

    create table dbval (k blob not null, primary key(k)) WITHOUT ROWID;

dbval only needs the key portion. Consequently, you are dealing with a sorted
set and Datascript's core is a
[persistent-sorted-set](https://github.com/tonsky/persistent-sorted-set).

I first assumed that I need to make one part of a datom mutable, so that I can
mark it as retracted. Until I discovered that the sorting of `t` in the Datomic
indexes `:eavt`, `:aevt`, `:avet` and `:vaet` allows to figure out what the
current state was at a given point in time, so that you can serve an immutable
database value. The secret sauce can be found in the `datoms-filter` transducer.

Back to FoundationDB, its keys and values are just byte arrays. The key contains
a tuple and its byte array representation allows to sort it, even if it is a mix
of different types (String, double, UUID, nested tuples, etc.). You will notice
that you can represent a (Datomic) datom as a tuple. Luckily, the tuple to byte
array logic is available via [Tuple
class](https://apple.github.io/foundationdb/javadoc/com/apple/foundationdb/tuple/Tuple.html).

Our current SaaS runs Datomic in production since 2018. Overall we are happy
with it. The biggest challenge for us were large migrations that have to be
split into many smaller transactions, to avoid that the transactor is occupied
for too long. Otherwise customers have to wait a couple of seconds before their
write is processed. The next generation of our SaaS runs one logical Datomic
database per customer (with a shared transactor pair) to avoid this issue. Your
challenges may vary but I think it's important that open source alternatives to
Datomic exist, so that you have the option to make different trade-offs.

Luckily there are already a couple of Datomic open source alternatives. Why
another one? Some does not offer you the database as a value. But the key is
that dbval tries to pick a minimal scope, since implementing a database almost
from scratch is a humongous task. For that reason dbval considers itself as a
database library and only tries to marry Datascript with Sqlite. Most
database-related features are already solved by Sqlite or its
[ecosystem](https://litestream.io/).


## TODOs

- Mature the library into something 'production-ready'

- Also adapt the ClojureScript parts (broken at the moment).

    - There is [a JS libary](https://github.com/josephg/fdb-tuple) that implements the FoundationDB tuple encoding.
    
    - Doing [synchronous SQLite reads and writes in JavaScript](https://blog.cloudflare.com/sqlite-in-durable-objects/#reads-and-writes-are-synchronous) is viable (no need to make everything async).

- Consider to increase `tx0`, `emax` and `txmax`

- Maybe: [UUIDs for entity IDs](https://tonsky.me/blog/datascript-2/)
