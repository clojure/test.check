(ns clojure.test.check.test.runner
  (:require [cljs.test :as test :refer-macros [run-tests]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.test]
            [clojure.test.check.random-test]
            [clojure.test.check.rose-tree-test]
            [clojure.test.check.clojure-test-test]))

(enable-console-print!)

(run-tests
  'clojure.test.check.test
  'clojure.test.check.random-test
  'clojure.test.check.rose-tree-test
  'clojure.test.check.clojure-test-test)

