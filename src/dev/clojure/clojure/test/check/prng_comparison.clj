(ns clojure.test.check.prng-comparison
  (:require [clojure.test.check.random :as r]
            [clojure.test.check.random-alt :as r']
            [criterium.core :as crit])
  (:import [java.util Random]
           [sun.misc Signal SignalHandler]))

;; Make the process quit when STDOUT stops being read
(Signal/handle (Signal. "PIPE")
               (reify SignalHandler
                 (handle [_ _]
                   (System/exit 0))))

(def splittable-impls
  {:AES
   (fn [^long seed] (r'/make-aes-random seed seed))
   :IJUSR
   (fn [^long seed] (r/make-java-util-splittable-random seed))
   :siphash
   (fn [^long seed] (r'/make-siphash-random seed))
   :SHA1 r'/make-sha1-random})

(defn lump
  [rng n f x]
  (if (zero? n)
    (f x (r/rand-long rng))
    (let [[rng1 rng2] (r/split rng)
          n-- (dec n)
          x' (lump rng1 n-- f x)]
      (if (reduced? x')
        x'
        (lump rng2 n-- f x')))))

(defn reduce-fibonacci-longs
  "Generates n longs from the given rng in a fashion that is somewhere
  in between balanced and linear."
  ([rng n f x] (reduce-fibonacci-longs rng n f x true))
  ([rng n f x left-side-heavy?]
     (if (= 1 n)
       (f x (r/rand-long rng))
       (loop [a 1, b 2]
         (if (>= b n)
           (let [heavy-count a
                 light-count (- n a)
                 left-count (if left-side-heavy? heavy-count light-count)
                 right-count (if left-side-heavy? light-count heavy-count)
                 [rng-left rng-right] (r/split rng)
                 not-left-side-heavy? (not left-side-heavy?)
                 x' (reduce-fibonacci-longs rng-left
                                            left-count
                                            f
                                            x
                                            not-left-side-heavy?)]
             (if (reduced? x')
               x'
               (reduce-fibonacci-longs rng-right
                                       right-count
                                       f
                                       x'
                                       not-left-side-heavy?)))
           (recur b (+ a b)))))))

(def linearization-strategies
  ;; have to use different self names here because weird compiler bug
  {:right-linear
   (fn [rng f x]
     (let [[rng1 rng2] (r/split rng)
           x' (f x (r/rand-long rng1))]
       (if (reduced? x')
         (deref x')
         (recur rng2 f x'))))
   :left-linear
   (fn  [rng f x]
     (let [[rng1 rng2] (r/split rng)
           x' (f x (r/rand-long rng2))]
       (if (reduced? x')
         (deref x')
         (recur rng1 f x'))))
   :alternating
   (fn [rng f x]
     (let [[rng1 rng2] (r/split rng)
           [rng3 rng4] (r/split rng1)
           x' (f x (r/rand-long rng2))]
       (if (reduced? x')
         (deref x')
         (let [x'' (f x' (r/rand-long rng3))]
           (if (reduced? x'')
             (deref x'')
             (recur rng4 f x''))))))
   ;; these two return "effectively" infinite seqs
   :balanced-63
   (fn [rng f x] (deref (lump rng 63 f x)))
   ;; this one should require twice as many calls to siphash as
   ;; balanced-63, so it's probably slower
   :balanced-64
   (fn [rng f x] (deref (lump rng 64 f x)))
   :right-lumpy
   (fn [rng f x]
     (let [[rng1 rng2] (r/split rng)
           x' (lump rng1 8 f x)]
       (if (reduced? x')
         (deref x')
         (recur rng2 f x'))))
   :left-lumpy
   (fn [rng f x]
     (let [[rng1 rng2] (r/split rng)
           x' (lump rng2 8 f x)]
       (if (reduced? x')
         (deref x')
         (recur rng1 f x'))))
   :fibonacci
   (let [infinity 1152921504606846976]
     (fn [rng f x]
       (reduce-fibonacci-longs rng infinity f x)))})

;; prints random data to STDOUT
(defn print-random
  [seed-str run-name]
  (let [daos (java.io.DataOutputStream. System/out)
        seed (Long/parseLong ^String seed-str)]
    (if (= run-name "JUR")
      (let [rng (java.util.Random. seed)]
        (loop [] (.writeLong daos (.nextLong rng)) (recur)))

      (let [[impl-name strategy-name] (clojure.string/split run-name #"-" 2)
            impl (splittable-impls (keyword impl-name))
            strategy (linearization-strategies (keyword strategy-name))]
        (strategy (impl seed)
                  (fn [_ ^long x]
                    (.writeLong daos x))
                  nil)))))

(defn xor-random-fn
  "Returns a 0-arg function that runs the xoring."
  [seed total-nums run-name]
  (cond

   (= run-name "JUR")
   (fn []
     (let [rng (java.util.Random. seed)]
       (loop [i 0, x 0]
         (if (= i total-nums)
           x
           (recur (inc i) (bit-xor x (.nextLong rng)))))))

   (= run-name "JUSR")
   (fn []
     (let [rng (java.util.SplittableRandom. seed)]
       (loop [i 0, x 0]
         (if (= i total-nums)
           x
           (recur (inc i) (bit-xor x (.nextLong rng)))))))

   (= run-name "JUR-lockless")
   (fn []
     (let [rng (clojure.test.check.JavaUtilRandom. seed)]
       (loop [i 0, x 0]
         (if (= i total-nums)
           x
           (recur (inc i) (bit-xor x (.nextLong rng)))))))


   :else
   (let [[impl-name strategy-name] (clojure.string/split run-name #"-" 2)
         impl (splittable-impls (keyword impl-name))
         strategy (linearization-strategies (keyword strategy-name))]
     (fn []
       (strategy (impl seed)
                 (fn [[x1 count] x2]
                   (let [count++ (inc count)
                         x3 (bit-xor x1 x2)]
                     (if (= count++ total-nums)
                       (reduced x3)
                       [x3 count++])))
                 [0 0])))))

(defn run-xor-random
  [seed-str longs-count-str run-name]
  (let [daos (java.io.DataOutputStream. System/out)
        seed (Long/parseLong ^String seed-str)
        longs-count (Long/parseLong ^String longs-count-str)
        f (xor-random-fn seed longs-count run-name)]
    (println (time (f)))))

(defn criterium
  [run-name]
  (let [f (xor-random-fn 42 1000000 run-name)
        {[mean] :mean
         [variance] :variance}
        (crit/benchmark* f nil)

        [factor unit] (crit/scale-time mean)
        mean-str (crit/format-value mean factor unit)

        stddev (Math/sqrt variance)
        [factor unit] (crit/scale-time stddev)
        stddev-str (crit/format-value stddev factor unit)]
    (println run-name mean-str "+/-" stddev-str)))

(defn criterium-all
  []
  (let [all-run-names
        (list* "JUR" "JUSR" "JUR-lockless"
               (for [impl-name (keys splittable-impls)
                     :when (not= :AES impl-name)
                     strategy-name (keys linearization-strategies)]
                 (str (name impl-name) "-" (name strategy-name))))]
    (doseq [run-name all-run-names] (criterium run-name))))
