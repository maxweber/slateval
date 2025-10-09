(ns datascript.conn
  (:require
    [datascript.db :as db #?@(:cljs [:refer [DB FilteredDB]])]
    [datascript.storage :as storage]
    [extend-clj.core :as extend]
    [me.tonsky.persistent-sorted-set :as set])
  #?(:clj
     (:import
       [datascript.db DB FilteredDB])))

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
   (let [q-max-tx (db/q-max-tx db)]
     (when-not (<= q-max-tx
                   (:max-tx db))
       (throw (ex-info "underlying tuple store has already been modified"
                       {:max-tx (:max-tx db)
                        :q-max-tx q-max-tx}))))
   (if (instance? FilteredDB db)
     (throw (ex-info "Filtered DB cannot be modified" {:error :transaction/filtered}))
     (db/transact-tx-data (db/->TxReport db db [] {} tx-meta) tx-data))))

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
   (conn-from-db (db/init-db datoms schema (storage/maybe-adapt-storage opts)))))

(defn create-conn
  ([]
   (conn-from-db (db/empty-db nil {})))
  ([schema]
   (conn-from-db (db/empty-db schema {})))
  ([schema opts]
   (conn-from-db (db/empty-db schema (storage/maybe-adapt-storage opts)))))

#?(:clj
   (defn restore-conn
     ([storage]
      (restore-conn storage {}))
     ([storage opts]
      (when-some [[db tail] (storage/restore-impl storage opts)]
        (make-conn
          {:db (storage/db-with-tail db tail)
           :tx-tail tail
           :db-last-stored db})))))

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
    #?(:clj
       (when-some [storage (storage/storage @conn)]
         (storage/store-impl! db (storage/storage-adapter db) true)
         (swap! (:atom conn) assoc
           :tx-tail []
           :db-last-stored db)))
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
