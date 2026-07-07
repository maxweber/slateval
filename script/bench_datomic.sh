#!/bin/bash
cd "`dirname $0`/.."

clojure -A:bench:datomic -M -m slateval.bench.datomic $@