(ns dbval.test.conn
  (:require
    [clojure.test :as t :refer [is are deftest testing]]
    [dbval.core :as d]
    [dbval.db :as db]
    [dbval.test.core :as tdc]))

(def schema
  {:aka {:db/cardinality :db.cardinality/many}})

;; Use a fixed UUID for deterministic testing
(def test-eid #uuid "11111111-1111-1111-1111-111111111111")

(def datoms
  #{(d/datom test-eid :age  17)
    (d/datom test-eid :name "Ivan")})

(deftest test-ways-to-create-conn
  (let [conn (d/create-conn)]
    (is (= #{} (set (d/datoms @conn :eavt))))
    (is (= nil (:schema @conn))))

  (let [conn (d/create-conn schema)]
    (is (= #{} (set (d/datoms @conn :eavt))))
    (is (= schema (:schema @conn))))

  (let [conn (d/conn-from-datoms datoms)]
    (is (= datoms (set (d/datoms @conn :eavt))))
    (is (= nil (:schema @conn))))

  (let [conn (d/conn-from-datoms datoms schema)]
    (is (= datoms (set (d/datoms @conn :eavt))))
    (is (= schema (:schema @conn))))

  (let [conn (d/conn-from-db (d/init-db datoms))]
    (is (= datoms (set (d/datoms @conn :eavt))))
    (is (= nil (:schema @conn))))

  (let [conn (d/conn-from-db (d/init-db datoms schema))]
    (is (= datoms (set (d/datoms @conn :eavt))))
    (is (= schema (:schema @conn)))))
