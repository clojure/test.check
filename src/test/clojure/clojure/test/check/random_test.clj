(ns clojure.test.check.random-test
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.random :as random]))

(def gen-split-steps
  (gen/list (gen/elements [:left :right])))

(defn apply-split-steps
  [rng steps]
  (if-let [[step & more] (seq steps)]
    (let [[rng1 rng2] (random/split rng)]
      (recur (case step :left rng1 :right rng2) more))
    rng))

(def gen-splitter
  "Generates a function that will repeatedly split a rng in random
  directions."
  (gen/fmap #(apply comp %) (gen/list gen-split-operation)))

(def gen-seed (gen/choose 0 Integer/MAX_VALUE))

(defspec determinism-spec
  ;; kind of weird trying to generate a random seed; gen/nat would
  ;; obviously be pretty bad.
  (prop/for-all [seed gen-seed
                 steps gen-split-steps]
    (let [r1 (random/make-random seed)
          r2 (random/make-random seed)]
      (= (-> r1 (apply-split-steps steps) (random/rand-long))
         (-> r2 (apply-split-steps steps) (random/rand-long))))))
