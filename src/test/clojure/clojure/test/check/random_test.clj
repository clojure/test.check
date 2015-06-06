(ns clojure.test.check.random-test
  "Tests of the custom RNG. This is a little weird since the subject
  of the tests (the random number generator) is also the primary
  internal driver of the tests, but hopefully it will still be
  meaningful."
  (:require [clojure.test.check.clojure-test :refer [defspec]]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.random :as random]))

(def gen-split-steps
  (gen/list (gen/elements [:left :right])))

(defn apply-split-steps
  [rng steps]
  (reduce (fn [rng step]
            (let [[rng1 rng2] (random/split rng)]
              (case step :left rng1 :right rng2)))
          rng
          steps))

(def gen-seed
  (let [gen-int (gen/choose 0 Integer/MAX_VALUE)]
    (gen/fmap (fn [[s1 s2]]
                (bit-or s1 (bit-shift-left s2 32)))
              (gen/tuple gen-int gen-int))))

(defspec determinism-spec
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
;; certain. The probability of a false failure (1/2^16384 or so) is
;; low enough to ignore.
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

;; Tests of the particular JavaUtilSplittableRandom impl, by
;; comparing with java.util.SplittableRandom on Java 8
(when (try (Class/forName "java.util.SplittableRandom")
           (catch ClassNotFoundException e))
  (eval
   '(defspec java-util-splittable-random-spec
      (prop/for-all [seed gen-seed
                     steps gen-split-steps]
        (let [immutable-rng (apply-split-steps
                             (random/make-java-util-splittable-random seed)
                             steps)
              mutable-rng
              ^java.util.SplittableRandom
              (reduce (fn [^java.util.SplittableRandom rng step]
                        (let [rng2 (.split rng)]
                          (case step :left rng :right rng2)))
                      (java.util.SplittableRandom. seed)
                      steps)]
          (= (random/rand-long immutable-rng)
             (.nextLong mutable-rng))))))

  ;; same test but for rand-double
  (eval
   '(defspec java-util-splittable-random-spec-double
      (prop/for-all [seed gen-seed
                     steps gen-split-steps]
        (let [immutable-rng (apply-split-steps
                             (random/make-java-util-splittable-random seed)
                             steps)
              mutable-rng
              ^java.util.SplittableRandom
              (reduce (fn [^java.util.SplittableRandom rng step]
                        (let [rng2 (.split rng)]
                          (case step :left rng :right rng2)))
                      (java.util.SplittableRandom. seed)
                      steps)]
          (= (random/rand-double immutable-rng)
             (.nextDouble mutable-rng)))))))

(defspec split-n-spec 40
  (prop/for-all [seed gen-seed
                 n gen/nat]
    (let [rng (random/make-random seed)]
      ;; checking that split-n returns the same generators that we
      ;; would get by doing a particular series of splits manually
      (= (map random/rand-long (random/split-n rng n))
         (map random/rand-long
              (if (zero? n)
                []
                (loop [v [], rng rng]
                  (if (= (dec n) (count v))
                    (conj v rng)
                    (let [[rng1 rng2] (random/split rng)]
                      (recur (conj v rng2) rng1))))))))))
