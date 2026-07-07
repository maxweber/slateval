(ns slateval.test
  (:require
    [clojure.test :as t :refer [is are deftest testing]]
    #?(:clj [clojure.java.shell :as sh])
    slateval.test.core
   
    slateval.test.components
    slateval.test.conn
    slateval.test.db
    slateval.test.entity
    slateval.test.explode
    slateval.test.filter
    slateval.test.ident
    slateval.test.index
    slateval.test.listen
    slateval.test.lookup-refs
    slateval.test.lru
    slateval.test.parser
    slateval.test.parser-find
    slateval.test.parser-return-map
    slateval.test.parser-rules
    slateval.test.parser-query
    slateval.test.parser-where
    slateval.test.pull-api
    slateval.test.pull-parser
    slateval.test.query
    slateval.test.query-aggregates
    slateval.test.query-find-specs
    slateval.test.query-fns
    slateval.test.query-not
    slateval.test.query-or
    slateval.test.query-pull
    slateval.test.query-return-map
    slateval.test.query-rules
    slateval.test.query-v3
    slateval.test.transact
    slateval.test.tuples
    slateval.test.validation
    slateval.test.upsert
    slateval.test.issues
    slateval.test.datafy))

(defn ^:export test-clj []
  (slateval.test.core/wrap-res #(t/run-all-tests #"slateval\..*")))

(defn ^:export test-cljs []
  (slateval.test.core/wrap-res #(t/run-all-tests #"slateval\..*")))

#?(:clj
   (defn test-node [& args]
     (let [res (apply sh/sh "node" "test_node.js" args)]
       (println (:out res))
       (binding [*out* *err*]
         (println (:err res)))
       (System/exit (:exit res)))))
