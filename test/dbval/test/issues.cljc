(ns dbval.test.issues
  (:require
    [dbval.core :as ds]
    [clojure.test :as t :refer [is are deftest testing]]))

(deftest ^{:doc "CLJS `apply` + `vector` will hold onto mutable array of arguments directly"}
  issue-262
  (let [db (ds/db-with (ds/empty-db)
             [{:attr "A"} {:attr "B"}])]
    (is (= (ds/q '[:find ?a ?b
                   :where [_ :attr ?a] 
                   [(vector ?a) ?b]]
             db)
          #{["A" ["A"]] ["B" ["B"]]}))))

#?(:clj
   (deftest ^{:doc "Can't pprint filtered db"}
     issue-330
     (let [base     (-> (ds/empty-db {:aka {:db/cardinality :db.cardinality/many}})
                      (ds/db-with [{:db/id -1
                                    :name  "Maksim"
                                    :age   45
                                    :aka   ["Max Otto von Stierlitz", "Jack Ryan"]}]))
           filtered (ds/filter base (constantly true))]
       (t/is (= (with-out-str (clojure.pprint/pprint base))
               (with-out-str (clojure.pprint/pprint filtered)))))))

(deftest ^{:doc "Can't diff databases with different types of the same attribute"}
  issue-369
  ;; Use fixed UUID for deterministic testing
  (let [eid #uuid "22222222-2222-2222-2222-222222222222"
        db1 (-> (ds/empty-db)
              (ds/db-with [[:db/add eid :attr :aa]]))
        db2 (-> (ds/empty-db)
              (ds/db-with [[:db/add eid :attr "aa"]]))
        [only-in-db1 only-in-db2 _common] (clojure.data/diff db1 db2)]
    ;; Check that diff produces datoms with correct attribute values
    (t/is (= 1 (count only-in-db1)))
    (t/is (= :aa (:v (first only-in-db1))))
    (t/is (= 1 (count only-in-db2)))
    (t/is (= "aa" (:v (first only-in-db2))))))

(deftest ^{:doc "Expose a schema as a part of the public API."}
  issue-381
  (let [schema {:aka {:db/cardinality :db.cardinality/many}}
        db     (ds/empty-db schema)]
    (t/is (= schema (ds/schema db)))))
