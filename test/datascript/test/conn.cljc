(ns datascript.test.conn
  (:require
    [clojure.test :as t :refer [is are deftest testing]]
    [datascript.core :as d]
    [datascript.db :as db]
    [datascript.test.core :as tdc]))

(def schema
  {:aka {:db/cardinality :db.cardinality/many}})

(def datoms
  #{(d/datom 1 :age  17)
    (d/datom 1 :name "Ivan")})

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
