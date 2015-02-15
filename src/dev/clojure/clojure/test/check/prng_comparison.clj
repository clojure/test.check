(ns clojure.test.check.prng-comparison
  (:require [clojure.test.check.random :as r]
            [criterium.core :as criterium])
  (:import [java.util Random]
           [org.apache.commons.math3.stat.inference ChiSquareTest]
           [sun.misc Signal SignalHandler]))

;; Make the process quit when STDOUT stops being read
(Signal/handle (Signal. "PIPE")
               (reify SignalHandler
                 (handle [_ _]
                   (System/exit 0))))

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
   (fn [^long seed] (r/make-aes-random seed seed))
   :siphash
   (fn [^long seed] (r/make-siphash-random seed))})

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

(defn right-linear-longs
  [rng n]
  (when (pos? n)
    (lazy-seq
     (let [[rng1 rng2] (r/split rng)]
       (cons (r/rand-long rng1)
             (right-linear-longs rng2 (dec n)))))))

(defn left-linear-longs
  [rng n]
  (when (pos? n)
    (lazy-seq
     (let [[rng1 rng2] (r/split rng)]
       (cons (r/rand-long rng2)
             (left-linear-longs rng1 (dec n)))))))

(defn balanced-longs
  [rng n]
  (loop [rngs [rng]
         count 1]
    (if (>= count n)
      (take n (map r/rand-long rngs))
      (recur (mapcat r/split rngs) (* 2 count)))))

(defn fibonacci-longs
  "Generates n longs from the given rng in a fashion that is somewhere
  in between balanced and linear."
  ([rng n] (fibonacci-longs rng n true))
  ([rng n left-side-heavy?]
     (if (= 1 n)
       [(r/rand-long rng)]
       (lazy-seq
        (loop [a 1, b 2]
          (if (>= b n)
            (let [heavy-count a
                  light-count (- n a)
                  left-count (if left-side-heavy? heavy-count light-count)
                  right-count (if left-side-heavy? light-count heavy-count)
                  [rng-left rng-right] (r/split rng)
                  not-left-side-heavy? (not left-side-heavy?)]
              (concat (fibonacci-longs rng-left left-count not-left-side-heavy?)
                      (fibonacci-longs rng-right right-count not-left-side-heavy?)))
            (recur b (+ a b))))))))

(def extractors
  {:left-linear (fn [rng-fn seed n] (left-linear-longs (rng-fn seed) n))
   :right-linear (fn [rng-fn seed n] (right-linear-longs (rng-fn seed) n))
   :balanced (fn [rng-fn seed n] (balanced-longs (rng-fn seed) n))
   :fibonacci (fn [rng-fn seed n] (fibonacci-longs (rng-fn seed) n))
   :sequential-seeds (fn [rng-fn seed n]
                       (->> (iterate inc seed)
                            (take n)
                            (map rng-fn)
                            (map r/rand-long)))})

(defn chi-square-test
  [expected-value actuals]
  (let [expecteds (into-array Double/TYPE (repeat (count actuals) expected-value))]
    (.chiSquareTest (ChiSquareTest.)
                    ^doubles expecteds
                    ^longs (into-array Long/TYPE actuals))))

(def some-seeds
  "These are all good seeds."
  [42 1421867162723 -1539333844256672508])

(defn bin-test
  [n xs]
  (let [expected (/ (count xs) (double n))
        freqs (->> xs (map #(mod % n)) (frequencies))
        actuals (->> (range n) (map #(get freqs % 0)))
        p (chi-square-test expected actuals)]
    (if (<= 0.05 p 0.95) :pass :fail)))

(def tests
  "Functions that take a collection of supposedly independent random
  longs and return :pass or :fail."
  {:bin100 (partial bin-test 100)
   :bin101 (partial bin-test 101)
   :bin20000 (partial bin-test 20000)})

(defn quality-tests
  []
  (println "Running all quality tests...")
  (doseq [[impl-name impl] impls
          [extractor-name extractor-fn] extractors
          [test-name test-fn] tests]
    (let [seeds (->> some-seeds
                     (map #(iterate inc %))
                     (apply interleave)
                     (take 30))
          {:keys [pass fail] :or {pass 0 fail 0}}
          (->> seeds
               (map #(extractor-fn impl % 200000))
               (map test-fn)
               (frequencies))]
      (printf "%15s -- %16s -- %10s -- %.2f\n"
              (name impl-name)
              (name extractor-name)
              (name test-name)
              (double (/ pass (+ pass fail))))
      (flush))))

(defn performance-tests
  []
  ;; eventually figure out a way to do a separate jvm run for each one
  ;; of these?
  (println "Running performance tests...")
  (let [{[mean] :mean} (criterium/benchmark
                        (let [r (java.util.Random. 42)]
                          (loop [i 1024 x 0]
                            (if (zero? i)
                              x
                              (recur (dec i) (bit-xor x (.nextLong r))))))
                        {})]
    (printf "Mutable JUR generating 1024 longs averaged %.2fµs\n"
            (* mean 1000000)))
  (let [rng (r/make-siphash-random 42)
        {[mean] :mean} (criterium/benchmark
                        (loop [i 1024 x 0 rng rng]
                          (if (zero? i)
                            x
                            (let [[rng1 rng2] (r/split rng)]
                              (recur (dec i) (bit-xor x (r/rand-long rng1)) rng2))))
                        {})]
    (printf "Low-allocation siphash generated 1024 longs averaged %.2fµs\n"
            (* mean 1000000)))
  (doseq [[impl-name impl] impls
          [extractor-name extractor-fn] extractors]
    (let [{[mean] :mean} (criterium/benchmark
                          (reduce bit-xor (extractor-fn impl 42 1024))
                          {})]
      (printf "%15s[%16s]: Generating 1024 longs averaged %.2fµs\n"
              (name impl-name)
              (name extractor-name)
              (* mean 1000000))
      (flush))))

(defn do-everything
  []
  (quality-tests)
  (performance-tests))
