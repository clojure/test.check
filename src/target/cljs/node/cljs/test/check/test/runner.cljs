(ns cljs.test.check.test.runner
  (:require [cljs.nodejs :as nodejs]
            [cljs.test :as test :refer-macros [run-tests]]
            [cljs.test.check.test]
            [cljs.test.check.rose-tree-test]
            [cljs.test.check.cljs-test-test]
            [cljs.test.check.generators :as gen]))

(nodejs/enable-util-print!)

(defn -main []
  (run-tests
    'cljs.test.check.test
    'cljs.test.check.rose-tree-test
    'cljs.test.check.cljs-test-test))

(set! *main-cli-fn* -main)
