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

(defn get-256-longs
  [rng]
  (map random/rand-long
       (nth (iterate #(mapcat random/split %) [rng]) 8)))

;; this spec is only statistically certain to pass, not logically
;; certain
(defspec different-states-spec
  (prop/for-all [seed gen-seed
                 pre-steps gen-split-steps
                 post-steps-1 gen-split-steps
                 post-steps-2 gen-split-steps]
    (let [r (random/make-random seed)
          r' (apply-split-steps r pre-steps)
          [r1 r2] (random/split r')
          r1' (apply-split-steps r1 post-steps-1)
          r2' (apply-split-steps r2 post-steps-2)]
      ;; r1' and r2' should not somehow be in the same state
      (not= (get-256-longs r1')
            (get-256-longs r2')))))
