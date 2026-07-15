#!/bin/bash
cd "`dirname $0`/.."

clojure -A:bench -M -m slateval.bench.run $@
