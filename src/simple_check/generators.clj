(ns simple-check.generators
  (:require [simple-check.util :as util])
  (:import java.util.Random)
  (:refer-clojure :exclude [int vector map keyword char]))

(defn random
  ([] (java.util.Random.))
  ([seed] (java.util.Random. seed)))

(defprotocol Shrink
  (shrink [this]))

(defn fmap
  [f gen]
  (fn [rand-seed size]
    (f (gen rand-seed size))))

(defn bind
  [gen k]
  (fn [rand-seed size]
    (let [value (gen rand-seed size)]
      ((k value) rand-seed size))))

;; Combinators
;; ---------------------------------------------------------------------------

(declare choose)

(defn one-of
  [generators]
  (bind (choose 0 (dec (count generators)))
        #(nth generators %)))

(defn elements
  [coll]
  (fmap #(nth coll %)
        (choose 0 (dec (count coll)))))

(defn such-that
  [gen f]
  (fn [rand-seed size]
    (let [value (gen rand-seed size)]
      (if (f value)
        value
        (recur rand-seed size)))))

;; Generators
;; ---------------------------------------------------------------------------

(defn- diff
  [min-range max-range]
  (case [(neg? min-range) (neg? max-range)]
    [true true] (Math/abs (- max-range min-range))
    ;; default
    (- )))

(defn choose
  [min-range max-range]
  (let [diff (Math/abs (- max-range min-range))]
    (fn [rand-seed _size]
      (if (zero? diff)
        min-range
        (+ (.nextInt rand-seed (inc diff)) min-range)))))

(defn pair
  [a b]
  (fn [rand-seed size]
    [(a rand-seed size)
     (b rand-seed size)]))

(defn sample
  ([generator]
   (repeatedly #(util/nullary-apply generator)))
  ([generator num-samples]
   (take num-samples (sample generator))))

(defn shrink-index
  [coll index]
  (clojure.core/map (partial assoc coll index) (shrink (coll index))))

(defn tuple
  [generators]
  (fn [rand-seed size]
    (vec (clojure.core/map #(% rand-seed size) generators))))

(defn shrink-tuple
  [value]
  (mapcat (partial shrink-index value) (range (count value))))

(defn halfs
  [n]
  (take-while (partial not= 0) (iterate #(quot % 2) n)))

(defn int
  ([] (int (random) 10000))
  ([rand-seed] (int rand-seed 10000))
  ([rand-seed size]
   ((choose (- 0 size) size)
      rand-seed size)))

(defn shrink-int
  [integer]
  (clojure.core/map (partial - integer) (halfs integer)))

(defn shrink-seq
  [coll]
  (if (empty? coll)
    coll
    (let [head (first coll)
          tail (rest coll)]
      (concat [tail]
              (for [x (shrink-seq tail)] (cons head x))
              (for [y (shrink head)] (cons y tail))))))

(defn vector
  [gen]
  (fn [rand-seed size]
    (let [num-elements (Math/abs (int rand-seed size))]
      (vec (repeatedly num-elements #(gen rand-seed size))))))

(defn map
  [key-gen val-gen]
  (fn [rand-seed size]
    (let [map-size ((choose 0 size) rand-seed size)
          pair (pair key-gen val-gen)]
      (into {} (repeatedly map-size #(pair rand-seed size))))))

(def char (fmap clojure.core/char (choose 0 255)))

(def char-ascii (fmap clojure.core/char (choose 32 126)))

(def char-alpha-numeric (fmap clojure.core/char
                              (one-of [(choose 48 57)
                                      (choose 65 90)
                                      (choose 97 122)])))

(defn string
  [rand-seed size]
  (apply str (repeatedly size #(char rand-seed size))))

(defn string-ascii
  [rand-seed size]
  (apply str (repeatedly size #(char-ascii rand-seed size))))

(defn string-alpha-numeric
  [rand-seed size]
  (apply str (repeatedly size #(char-alpha-numeric rand-seed size))))

(def keyword (fmap clojure.core/keyword string-alpha-numeric))

(defn shrink-map
  [value]
  [])

;; Instances
;; ---------------------------------------------------------------------------

(extend java.lang.Number
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink shrink-int})

(extend clojure.lang.IPersistentVector
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink (comp (partial clojure.core/map vec) shrink-seq)})

(extend clojure.lang.PersistentList
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink (comp (partial clojure.core/map (partial apply list)) shrink-seq)})

(extend clojure.lang.IPersistentMap
  Shrink
  {:shrink shrink-map})
