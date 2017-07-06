(ns clojure.test.check.clojure-test.assertions.cljs
  (:require
   [cljs.test]
   [clojure.test.check.clojure-test.assertions :as assertions]))

(defmethod cljs.test/assert-expr 'clojure.test.check.clojure-test/check?
  [_ msg form]
  (assertions/check? msg form))
