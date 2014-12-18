(ns cljs.test.check.test.runner
  (:require [cljs.test :as test :refer-macros [run-tests]]
            [cljs.test.check.generators :as gen]
            [cljs.test.check.test]
            [cljs.test.check.rose-tree-test]
            [cljs.test.check.cljs-test-test]))

(enable-console-print!)

(run-tests
  'cljs.test.check.test
  'cljs.test.check.rose-tree-test
  'cljs.test.check.cljs-test-test)

