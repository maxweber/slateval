(ns slateval.test.ident
  (:require
    [clojure.test :as t :refer [is are deftest testing]]
    [slateval.core :as d]))

(def *db
  (delay
    (let [tx (d/with (d/empty-db {:ref {:db/valueType :db.type/ref}})
               [[:db/add "e1" :db/ident :ent1]
                [:db/add "e2" :db/ident :ent2]
                [:db/add "e2" :ref "e1"]])]
      {:db (:db-after tx)
       :e1 (get (:tempids tx) "e1")
       :e2 (get (:tempids tx) "e2")})))

(deftest test-q
  (let [{:keys [db e1 e2]} @*db]
    (is (= e1 (d/q '[:find ?v .
                     :where [:ent2 :ref ?v]] db)))
    (is (= e2 (d/q '[:find ?f .
                     :where [?f :ref :ent1]] db)))))

(deftest test-transact!
  (let [{:keys [db]} @*db
        db' (d/db-with db [[:db/add :ent1 :ref :ent2]])]
    ;; like Datomic's entity API, a ref to an entity with a :db/ident
    ;; collapses to the ident keyword
    (is (= :ent2 (-> (d/entity db' :ent1) :ref)))))

(deftest test-entity
  (let [{:keys [db]} @*db]
    (is (= {:db/ident :ent1}
          (into {} (d/entity db :ent1))))))

(deftest test-pull
  (let [{:keys [db e1]} @*db]
    (is (= {:db/id e1, :db/ident :ent1}
          (d/pull db '[*] :ent1)))))
