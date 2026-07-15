(ns slateval.test.db
  (:require
    [clojure.data]
    [clojure.test :as t :refer [is are deftest testing]]
    [slateval.core :as d]
    [slateval.db :as db :refer [defrecord-updatable]]
    [slateval.test.core]))

;;
;; verify that defrecord-updatable works with compiler/core macro configuration
;; define dummy class which redefines hash, could produce either
;; compiler or runtime error
;;
(defrecord-updatable HashBeef [x]
  clojure.lang.IHashEq (hasheq [hb] 0xBEEF))

(deftest test-defrecord-updatable
  (is (= 0xBEEF (-> (map->HashBeef {:x :ignored}) hash))))


;; whitebox test to confirm that hash cache caches
(deftest test-db-hash-cache
  (let [db (d/empty-db)]
    (is (= 0         @(.-hash db)))
    (let [h (hash db)]
      (is (= h @(.-hash db))))))


(deftest test-empty-vector-value
  ;; regression: storing an empty vector NPEd in `tuple` — the & rest args
  ;; are nil for zero components, but Tuple.addAll requires a List
  (let [tx (d/with (d/empty-db) [{:db/id "e1" :path []}])
        e1 (get (:tempids tx) "e1")
        db (:db-after tx)]
    (is (= [[]] (mapv :v (d/datoms db :eavt e1 :path))))))

(deftest test-diff
  ;; clojure.data/diff is deliberately unsupported: slateval databases may
  ;; not fit into memory, and a diff would have to realize both sides
  ;; entirely. The DB record still extends clojure.data/Diff, but only to
  ;; throw — otherwise diff would fall back to clojure.data's default map
  ;; implementation and diff the record's fields.
  (let [tx1 (d/with (d/empty-db) [{:db/id "e1" :a 1 :b 2}])
        db1 (:db-after tx1)
        e1 (get (:tempids tx1) "e1")
        db2 (d/db-with db1 [[:db/retract e1 :b 2]
                            [:db/add e1 :c 3]])]
    (is (thrown-msg? "clojure.data/diff is not supported on slateval databases, since it would realize both databases entirely in memory"
          (clojure.data/diff db1 db2)))))
