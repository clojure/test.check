(ns simple-check.generators
  (:import java.util.Random)
  (:refer-clojure :exclude [int vector list map keyword char boolean]))


;; TODO: namespace the :gen tag

;; Helpers
;; ---------------------------------------------------------------------------

(defn random
  ([] (java.util.Random.))
  ([seed] (java.util.Random. seed)))

(defn call-gen
  [[tag generator-fn] rand-seed size]
  (generator-fn rand-seed size))

(defn make-size-range-seq
  [max-size]
  (cycle (range 1 max-size)))

(defn sample-seq
  ([generator] (sample-seq generator 100))
  ([generator max-size]
   (let [r (random)
         size-seq (make-size-range-seq max-size)]
     (clojure.core/map (partial call-gen generator r) size-seq))))

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
  [:gen (fn [rand-seed size]
          (f (call-gen gen rand-seed size)))])

(defn bind
  [gen k]
  [:gen (fn [rand-seed size]
          (let [value (call-gen gen rand-seed size)]
            (call-gen (k value) rand-seed size)))])

(defn return
  [val]
  [:gen (fn [rand-seed size] val)])

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
    [:gen (fn [rand-seed _size]
      (if (zero? diff)
        min-range
        (+ (.nextInt rand-seed (inc diff)) min-range)))]))


(defn one-of
  [generators]
  (bind (choose 0 (dec (count generators)))
        #(nth generators %)))

(defn- pick
  [[h & tail] n]
  (let [[chance gen] h]
    (if (<= n chance)
      gen
      (recur tail (- n chance)))))

(defn frequency
  [pairs]
  (let [total (apply + (clojure.core/map first pairs))]
    (bind (choose 1 total)
          (partial pick pairs))))

(defn elements
  [coll]
  (fmap #(nth coll %)
        (choose 0 (dec (count coll)))))

(defn such-that
  [gen f]
  [:gen (fn [rand-seed size]
    (let [value (call-gen gen rand-seed size)]
      (if (f value)
        value
        (recur rand-seed size))))])

;; Generic generators and helpers
;; ---------------------------------------------------------------------------

(defn pair
  [a b]
  [:gen (fn [rand-seed size]
    [(call-gen a rand-seed size)
     (call-gen b rand-seed size)])])

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

(defn int-gen
  ([rand-seed size]
   (call-gen (choose (- size) size)
      rand-seed size)))

(def int [:gen int-gen])

(def pos-int (fmap #(Math/abs %) int))
(def neg-int (fmap (partial * -1) pos-int))

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
  [:gen (fn [rand-seed size]
    (vec (clojure.core/map #(call-gen % rand-seed size) generators)))])

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
  [:gen (fn [rand-seed size]
    (let [num-elements (Math/abs (call-gen int rand-seed size))]
      (vec (repeatedly num-elements #(call-gen gen rand-seed size)))))])

(extend clojure.lang.IPersistentVector
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink (comp (partial clojure.core/map vec) shrink-seq)})

;; List
;; ---------------------------------------------------------------------------

(defn list
  [gen]
  [:gen (fn [rand-seed size]
    (let [num-elements (Math/abs (call-gen int rand-seed size))]
      (into '() (repeatedly num-elements #(call-gen gen rand-seed size)))))])

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
  [:gen (fn [rand-seed size]
    (let [map-size (call-gen (choose 0 size) rand-seed size)
          p (pair key-gen val-gen)]
      (into {} (repeatedly map-size #(call-gen p rand-seed size)))))])

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

;; TODO: make strings use the full utf-8 range

(defn string-gen
  [rand-seed size]
  (clojure.string/join (repeatedly size #(call-gen char rand-seed size))))

(def string [:gen string-gen])

(defn string-ascii-gen
  [rand-seed size]
  (clojure.string/join (repeatedly size #(call-gen char-ascii rand-seed size))))

(def string-ascii [:gen string-ascii-gen])

(defn string-alpha-numeric-gen
  [rand-seed size]
  (clojure.string/join (repeatedly size #(call-gen char-alpha-numeric rand-seed size))))

(def string-alpha-numeric [:gen string-alpha-numeric-gen])

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
