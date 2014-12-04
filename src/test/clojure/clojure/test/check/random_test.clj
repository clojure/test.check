(ns clojure.test.check.random-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.random :as random]))

(def gen-split-operation
  (gen/elements [(comp first random/split)
                 (comp second random/split)]))

(defspec determinism-spec
  ;; kind of weird trying to generate a random seed; gen/nat would
  ;; obviously be pretty bad.
  (prop/for-all [seed (gen/choose 0 Integer/MAX_VALUE)
                 ops (gen/list gen-split-operation)]
    (let [r1 (random/make-random seed)
          r2 (random/make-random seed)
          func (apply comp random/rand-long ops)]
      (= (func r1) (func r2)))))
