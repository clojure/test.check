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
  "Returns a lazy seq of 2^n longs from the given rng."
  [rng n]
  (map r/rand-long
       (nth (iterate #(mapcat r/split %) [rng]) n)))

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
   (fn [rng] (lump rng 63))
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
     (fn [rng] (fibonacci-longs rng infinity)))})

;; prints random data to STDOUT
(defn -main
  [seed-str impl-str & [strategy-str]]
  (let [daos (java.io.DataOutputStream. System/out)
        seed (Long/parseLong ^String seed-str)]
    (if (= impl-str "JUR")
      (let [rng (java.util.Random. seed)]
        (loop [] (.writeLong daos (.nextLong rng)) (recur)))
      ;; is the perf here dominated by lazy seq operations?  is it
      ;; worth the bother to eliminate that?
      (let [impl (splittable-impls (keyword impl-str))
            strategy (linearization-strategies (keyword strategy-str))]
        (doseq [long (strategy (impl seed))]
          (.writeLong daos long))))))
