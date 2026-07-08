(ns slateval.test.time-travel
  (:require
    [clojure.test :as t :refer [is deftest testing]]
    [slateval.core :as d]
    #?(:clj [com.yetanalytics.squuid :as squuid])))

#?(:clj
   (defn- setup
     "Returns {:conn .. :tx1 .. :db ..} with two transactions:
      tx1 asserts Alice/30, tx2 updates Alice to 31 and adds Bob/25."
     []
     (let [conn (d/create-conn {:name {:db/unique :db.unique/identity}})
           r1   (d/transact! conn [{:name "Alice" :age 30}])
           _    (d/transact! conn [{:name "Alice" :age 31}
                                   {:name "Bob" :age 25}])]
       {:conn conn
        :tx1  (:tx r1)
        :db   (d/db conn)})))

#?(:clj
   (deftest test-as-of
     (let [{:keys [tx1 db]} (setup)]
       (testing "current value sees the update and Bob"
         (is (= #{["Alice" 31] ["Bob" 25]}
                (d/q '[:find ?n ?a :where [?e :name ?n] [?e :age ?a]] db))))
       (testing "as-of a tx squuid sees the past"
         (let [past (d/as-of db tx1)]
           (is (= #{["Alice" 30]}
                  (d/q '[:find ?n ?a :where [?e :name ?n] [?e :age ?a]] past)))
           (is (= tx1 (d/as-of-t past)))))
       (testing "as-of an instant"
         (let [inst (.plusMillis ^java.time.Instant (squuid/uuid->time tx1) 1)
               past (d/as-of db inst)]
           (is (= #{["Alice" 30]}
                  (d/q '[:find ?n ?a :where [?e :name ?n] [?e :age ?a]] past)))))
       (testing "as-of view is read-only"
         (is (thrown-with-msg? Exception #"Cannot transact"
               (d/with (d/as-of db tx1) [{:name "Carol"}])))))))

#?(:clj
   (deftest test-since
     (let [{:keys [tx1 db]} (setup)]
       (testing "since only sees datoms asserted after t"
         (is (= #{["Bob"]}
                (d/q '[:find ?n :where [?e :name ?n]] (d/since db tx1))))
         (is (= tx1 (d/since-t (d/since db tx1))))))))

#?(:clj
   (deftest test-history
     (let [{:keys [db]} (setup)
           history (d/history db)]
       (testing "history exposes retracted datom versions"
         (is (= #{[30] [31] [25]}
                (d/q '[:find ?a :where [_ :age ?a]] history))))
       (testing "a 5th pattern element binds the assert/retract flag like Datomic"
         (is (= #{[30 true] [30 false] [31 true] [25 true]}
                (d/q '[:find ?a ?added :where [_ :age ?a _ ?added]] history)))
         (is (= #{[30]}
                (d/q '[:find ?a :where [_ :age ?a _ false]] history)))
         (is (= #{[30] [31] [25]}
                (d/q '[:find ?a :where [_ :age ?a _ true]] history))))
       (testing "history view is read-only"
         (is (thrown-with-msg? Exception #"Cannot transact"
               (d/with history [{:name "Carol"}])))))))

#?(:clj
   (deftest test-with-dry-run
     (let [{:keys [conn db]} (setup)]
       (testing "speculative datoms are visible on db-after only"
         (let [report (d/with-dry-run db [{:name "Carol" :age 40}])]
           (is (= #{["Alice"] ["Bob"] ["Carol"]}
                  (d/q '[:find ?n :where [?e :name ?n]] (:db-after report))))
           (is (contains? (:tempids report) :db/current-tx))
           (is (= #{["Alice"] ["Bob"]}
                  (d/q '[:find ?n :where [?e :name ?n]] (d/db conn))))))
       (testing "chained dry-runs accumulate speculative datoms"
         (let [r1 (d/with-dry-run db [{:name "Carol"}])
               r2 (d/with-dry-run (:db-after r1) [{:name "Dave"}])]
           (is (= #{["Alice"] ["Bob"] ["Carol"] ["Dave"]}
                  (d/q '[:find ?n :where [?e :name ?n]] (:db-after r2))))))
       (testing "a real transact against a speculative value throws"
         (let [speculative (:db-after (d/with-dry-run db [{:name "Carol"}]))]
           (is (thrown-with-msg? Exception #"speculative"
                 (d/with speculative [{:name "Eve"}])))))
       (testing "the connection still accepts real transactions afterwards"
         (d/with-dry-run (d/db conn) [{:name "Frank"}])
         (d/transact! conn [{:name "Grace"}])
         (is (= #{["Alice"] ["Bob"] ["Grace"]}
                (d/q '[:find ?n :where [?e :name ?n]] (d/db conn))))))))
