(ns clojure.test.check.prng-comparison
  (:require [clojure.test.check.random :as r])
  (:import [java.util Random]
           [sun.misc Signal SignalHandler]))

;; Make the process quit when STDOUT stops being read
(Signal/handle (Signal. "PIPE")
               (reify SignalHandler
                 (handle [_ _]
                   (System/exit 0))))

(def splittable-impls
  {:AES
   (fn [^long seed] (r/make-aes-random seed seed))
   :siphash
   (fn [^long seed] (r/make-siphash-random seed))})

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
   (fn self1 [rng]
     (lazy-seq
      (let [[rng1 rng2] (r/split rng)]
        (cons (r/rand-long rng1) (self1 rng2)))))
   :left-linear
   (fn self2 [rng]
     (lazy-seq
      (let [[rng1 rng2] (r/split rng)]
        (cons (r/rand-long rng2) (self2 rng1)))))
   :alternating
   (fn self3 [rng]
     (lazy-seq
      (let [[rng1 rng2] (r/split rng)
            [rng3 rng4] (r/split rng1)]
        (cons (r/rand-long rng2)
              (cons (r/rand-long rng3)
                    (self3 rng4))))))
   ;; these two return "effectively" infinite seqs
   :balanced-63
   (vary-meta
    (fn [rng f x] (lump rng 63 f x))
    assoc ::reduction? true)
   ;; this one should require twice as many calls to siphash as
   ;; balanced-63, so it's probably slower
   :balanced-64
   (fn [rng] (lump rng 64))
   :right-lumpy
   (fn self4 [rng]
     (lazy-seq
      (let [[rng1 rng2] (r/split rng)]
        (concat (lump rng1 8) (self4 rng2)))))
   :left-lumpy
   (fn self5 [rng]
     (lazy-seq
      (let [[rng1 rng2] (r/split rng)]
        (concat (lump rng2 8) (self5 rng1)))))
   :fibonacci
   (let [infinity 1152921504606846976]
     (vary-meta
      (fn [rng f x]
        (reduce-fibonacci-longs rng infinity f x))
      assoc ::reduction? true))})

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
        (if (::reduction? (meta strategy))
          (strategy (impl seed)
                    (fn [_ ^long x]
                      (.writeLong daos x))
                    nil)
          (doseq [long (strategy (impl seed))]
            (.writeLong daos long)))))))
