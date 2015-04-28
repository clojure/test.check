(use 'criterium.core)
(require 'clojure.test
         '[clojure.test.check clojure-test-test rose-tree-test test])

(println "Benchmarking the test suite...")

(bench
 (binding [clojure.test/*test-out* (java.io.StringWriter.)
           *out* (java.io.StringWriter.)]
   (clojure.test/run-all-tests)))
