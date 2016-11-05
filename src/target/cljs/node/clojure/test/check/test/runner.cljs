(ns clojure.test.check.test.runner
  (:require [cljs.nodejs :as nodejs]
            [cljs.test :as test :refer-macros [run-tests]]
            [cljs.spec.test :as spec-test]
            [clojure.test.check.test]
            [clojure.test.check.random-test]
            [clojure.test.check.rose-tree-test]
            [clojure.test.check.clojure-test-test]
            [clojure.test.check.specs :as specs]
            [clojure.test.check.generators :as gen]))

(nodejs/enable-util-print!)

(defn -main []
  (println (spec-test/instrument))
  (run-tests
    'clojure.test.check.test
    'clojure.test.check.random-test
    'clojure.test.check.rose-tree-test
    'clojure.test.check.clojure-test-test))

(set! *main-cli-fn* -main)
