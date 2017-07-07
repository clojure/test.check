(ns clojure.test.check.async-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.test.check :as t.c]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

;;
;; To run these in CLJS we'd have to adapt them to however cljs.test
;; does async tests.
;;

(deftest async-quick-check-success-test
  (let [p (promise)
        prop (prop/for-all-async callback
                                 [x gen/nat]
                                 (callback (not= x -14)))
        ret (t.c/quick-check-async 10000
                                   prop
                                   (partial deliver p)
                                   :seed 42)]
    (is (nil? ret))
    (is (true? (:result @p)))
    (is (= 10000 (:num-tests @p)))))

(deftest async-quick-check-failure-test
  (let [p (promise)
        prop (prop/for-all-async callback
                                 [x gen/nat]
                                 (callback (not= x 14)))
        ret (t.c/quick-check-async 10000
                                   prop
                                   (partial deliver p)
                                   :seed 42)]
    (is (nil? ret))
    (is (false? (:result @p)))
    (is (-> @p :shrunk :smallest first (= 14)))))
