(ns simple-check.generators
  (:require [simple-check.util :as util])
  (:import java.util.Random)
  (:refer-clojure :exclude [int vector list map keyword char boolean]))

;; Helpers
;; ---------------------------------------------------------------------------

(defn random
  ([] (java.util.Random.))
  ([seed] (java.util.Random. seed)))

(defn sample-seq
  ;; TODO: don't hardcode 10 here, this should be
  ;; a size seq, like we do in testing.
  ([generator] (sample-seq generator 10))
  ([generator size]
   (let [r (random)]
     (repeatedly #(generator r size)))))

(defn sample
  ([generator]
   (sample generator 10))
  ([generator num-samples]
   (take num-samples (sample-seq generator))))

;; Shrink protocol, Functor and Monad implementations
;; ---------------------------------------------------------------------------

(defprotocol Shrink
  (shrink [this]))

(defn fmap
  "NOTE: since `gen` is a function, this is just equivalent
  to `comp`. Perhaps using fmap will be more confusing to people?"
  [f gen]
  (fn [rand-seed size]
    (f (gen rand-seed size))))

(defn bind
  [gen k]
  (fn [rand-seed size]
    (let [value (gen rand-seed size)]
      ((k value) rand-seed size))))

(def return constantly)

;; Combinators
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

;; Generic generators and helpers
;; ---------------------------------------------------------------------------

(defn pair
  [a b]
  (fn [rand-seed size]
    [(a rand-seed size)
     (b rand-seed size)]))

(defn shrink-index
  [coll index]
  (clojure.core/map (partial assoc coll index) (shrink (coll index))))

(defn shrink-seq
  [coll]
  (if (empty? coll)
    coll
    (let [head (first coll)
          tail (rest coll)]
      (concat [tail]
              (for [x (shrink-seq tail)] (cons head x))
              (for [y (shrink head)] (cons y tail))))))

(defn halfs
  [n]
  (take-while (partial not= 0) (iterate #(quot % 2) n)))

;; Boolean
;; ---------------------------------------------------------------------------

(def boolean (elements [true false]))

(defn shrink-boolean
  [b]
  (if b [false] []))

(extend java.lang.Boolean
  Shrink
  {:shrink shrink-boolean})

;; Number
;; ---------------------------------------------------------------------------

(defn int
  ([] (int (random) 10000))
  ([rand-seed] (int rand-seed 10000))
  ([rand-seed size]
   ((choose (- 0 size) size)
      rand-seed size)))

(defn shrink-int
  [integer]
  (clojure.core/map (partial - integer) (halfs integer)))

(extend java.lang.Number
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink shrink-int})

;; Tuple
;; ---------------------------------------------------------------------------

(defn tuple
  [generators]
  (fn [rand-seed size]
    (vec (clojure.core/map #(% rand-seed size) generators))))

(defn shrink-tuple
  [value]
  (mapcat (partial shrink-index value) (range (count value))))

;; TODO: not sure what to do about `extend` here, maybe we could
;; make a tuple type with `deftype`? Wish we had something more
;; akin to Haskell's `newtype` here

;; Vector
;; ---------------------------------------------------------------------------

(defn vector
  [gen]
  (fn [rand-seed size]
    (let [num-elements (Math/abs (int rand-seed size))]
      (vec (repeatedly num-elements #(gen rand-seed size))))))

(extend clojure.lang.IPersistentVector
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink (comp (partial clojure.core/map vec) shrink-seq)})

;; List
;; ---------------------------------------------------------------------------

(defn list
  [gen]
  (fn [rand-seed size]
    (let [num-elements (Math/abs (int rand-seed size))]
      (into '() (repeatedly num-elements #(gen rand-seed size))))))

(defn shrink-list
  [l]
  (clojure.core/map (partial apply clojure.core/list) (shrink-seq l)))

(extend clojure.lang.PersistentList
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink shrink-list})

;; Map
;; ---------------------------------------------------------------------------

(defn map
  [key-gen val-gen]
  (fn [rand-seed size]
    (let [map-size ((choose 0 size) rand-seed size)
          pair (pair key-gen val-gen)]
      (into {} (repeatedly map-size #(pair rand-seed size))))))

(defn shrink-map
  [value]
  [])
(extend clojure.lang.IPersistentMap
  Shrink
  {:shrink shrink-map})

;; Character
;; (generator and shrink strategy pretty much ripped from Haskell impl.)
;; ---------------------------------------------------------------------------

(def char (fmap clojure.core/char (choose 0 255)))

(def char-ascii (fmap clojure.core/char (choose 32 126)))

(def char-alpha-numeric (fmap clojure.core/char
                              (one-of [(choose 48 57)
                                       (choose 65 90)
                                       (choose 97 122)])))

(defn- stamp
  [c]
  [(not (Character/isLowerCase c))
   (not (Character/isUpperCase c))
   (not (Character/isDigit c))
   (not= \space c)
   c])

(defn- <-stamp
  [a b]
  (neg? (compare (stamp a) (stamp b))))

(defn shrink-char
  [c]
  (filter
    #(<-stamp % c)
    (concat [\a \b \c]
            (for [x [c] :while #(Character/isUpperCase %)]
              (Character/toLowerCase x))
            [\A \B \C
             \1 \2 \3
             ;; TODO: should newline be here? It will make ascii chars
             ;; shrink incorrectly. But it's also useful for finding bugs...
             ;; \newline
             \space])))

(extend java.lang.Character
  Shrink
  {:shrink shrink-char})

;; String
;; ---------------------------------------------------------------------------

(defn string
  [rand-seed size]
  (clojure.string/join (repeatedly size #(char rand-seed size))))

(defn string-ascii
  [rand-seed size]
  (clojure.string/join (repeatedly size #(char-ascii rand-seed size))))

(defn string-alpha-numeric
  [rand-seed size]
  (clojure.string/join (repeatedly size #(char-alpha-numeric rand-seed size))))

(defn shrink-string
  [s]
  (clojure.core/map clojure.string/join (shrink-seq s)))

(extend java.lang.String
  Shrink
  {:shrink shrink-string})

;; Keyword
;; ---------------------------------------------------------------------------

(def keyword (fmap clojure.core/keyword
                   (such-that string-alpha-numeric #(not= "" %))))

(defn shrink-keyword
  [k]
  (clojure.core/map clojure.core/keyword
                    (-> k str
                      rest
                      clojure.string/join
                      shrink-string)))

(extend clojure.lang.Keyword
  Shrink
  {:shrink shrink-keyword})
