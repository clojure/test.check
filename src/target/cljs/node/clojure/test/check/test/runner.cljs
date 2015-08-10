(ns clojure.test.check.test.runner
  (:require [cljs.nodejs :as nodejs]
            [cljs.test :as test :refer-macros [run-tests]]
            [clojure.test.check.test]
            [clojure.test.check.random-test]
            [clojure.test.check.rose-tree-test]
            [clojure.test.check.clojure-test-test]
            [clojure.test.check.generators :as gen]))

(nodejs/enable-util-print!)

(defn -main []
  (run-tests
    'clojure.test.check.test
    'clojure.test.check.random-test
    'clojure.test.check.rose-tree-test
    'clojure.test.check.clojure-test-test))

(set! *main-cli-fn* -main)
