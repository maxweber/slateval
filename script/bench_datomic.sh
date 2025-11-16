#!/bin/bash
cd "`dirname $0`/.."

clojure -A:bench:datomic -M -m dbval.bench.datomic $@