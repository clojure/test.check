(ns user
  (:require [clojure.test.check.random :as r])
  (:import [java.util Random]
           [org.apache.commons.math3.stat.inference ChiSquareTest]))

(def impls
  {:naive-JUR
   (fn self1 [^long seed]
     (let [r (Random. seed)
           x1 (.nextLong r)
           x2 (.nextLong r)]
       ;; no reason to think this will work well
       (reify r/IRandom
         (split [_] [(self1 x1) (self1 x2)])
         (rand-long [_] x1))))
   :ad-hoc-JUR
   ;; try to do slightly better than naive-JUR by doing ad-hoc tricks
   (fn self2 [^long seed]
     (let [r (Random. seed)
           x1 (.nextLong r)
           x2 (.nextLong r)
           x3 (.nextLong r)
           x4 (.nextLong r)]
       (reify r/IRandom
         (split [_] [(self2 (bit-xor x1 x3)) (self2 (bit-xor x2 x4))])
         (rand-long [_] x1))))
   ;; Expecting AES to be high quality by all measures, but poor
   ;; performance.
   :AES
   (fn [^long seed] (r/make-aes-random seed seed))})

(defn stats
  [xs]
  (let [c (double (count xs))
        mean (/ (reduce + xs) c)
        variance (/ (->> xs
                         (map #(- mean %))
                         (map #(* % %))
                         (reduce +))
                    c)]
    {:mean mean :variance variance}))

(defn linear-longs
  [rng n]
  (when (pos? n)
    (lazy-seq
     (let [[rng1 rng2] (r/split rng)]
       (cons (r/rand-long rng1)
             (linear-longs rng2 (dec n)))))))

(defn balanced-longs
  [rng n]
  (loop [rngs [rng]
         count 1]
    (if (>= count n)
      (take n (map r/rand-long rngs))
      (recur (mapcat r/split rngs) (* 2 count)))))

;; TODO: some third option for unbalanced? how do we do that
;; deterministically?
(def extractors {:linear linear-longs, :balanced balanced-longs})

(defn chi-square-test
  [expected-value actuals]
  (let [expecteds (into-array Double/TYPE (repeat (count actuals) expected-value))]
    (.chiSquareTest (ChiSquareTest.)
                    ^doubles expecteds
                    ^longs (into-array Long/TYPE actuals))))

(def some-seeds
  "These are all good seeds."
  [42 1421867162723 -1539333844256672508])

(def tests
  "Functions that take a collection of supposedly independent random
  longs and return :pass or :fail."
  {:bin100
   (fn [xs]
     (let [expected (/ (count xs) 100.0)
           p (chi-square-test expected (->> xs
                                            (map #(mod % 100))
                                            (frequencies)
                                            (vals)))]
       (if (<= 0.05 p 0.95) :pass :fail)))})

(defn do-everything
  []
  (println "Comparing all PRNGs with all tests")
  (doseq [[impl-name impl] impls
          [extractor-name extractor-fn] extractors
          [test-name test-fn] tests]
    (let [seeds (->> some-seeds
                     (map #(iterate inc %))
                     (apply interleave)
                     (take 30))
          {:keys [pass fail] :or {pass 0 fail 0}}
          (->> seeds
               (map impl)
               (map #(extractor-fn % 100000))
               (map test-fn)
               (frequencies))]
      (printf "%15s -- %10s -- %10s -- %.2f\n"
              (name impl-name)
              (name extractor-name)
              (name test-name)
              (double (/ pass (+ pass fail))))
      (flush))))
