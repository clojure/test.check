(ns simple-check.generators
  (:import java.util.Random)
  (:refer-clojure :exclude [int vector list map keyword
                            char boolean byte bytes]))


;; TODO: namespace the :gen tag?

;; Helpers
;; ---------------------------------------------------------------------------

(defn random
  ([] (Random.))
  ([seed] (Random. seed)))

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
  "Return a sequence of `num-samples` (default 10)
  realized values from `generator`."
  ([generator]
   (sample generator 10))
  ([generator num-samples]
   (take num-samples (sample-seq generator))))

;; Shrink protocol, Functor and Monad implementations
;; ---------------------------------------------------------------------------

(defprotocol Shrink
  "The Shrink protocol exists for dispatching the shrink function
  based on type."
  (shrink [this]
          "Return a (possibly empty) sequence of smaller values.
          Care must be given to not create any loops if shrink is called
          recursively on the sequence."))

(defn fmap
  "Create a new generator that calls `f` on the generated value before
  returning it"
  [f gen]
  [:gen (fn [rand-seed size]
          (f (call-gen gen rand-seed size)))])

(defn bind
  "Monadic bind"
  [gen k]
  [:gen (fn [rand-seed size]
          (let [value (call-gen gen rand-seed size)]
            (call-gen (k value) rand-seed size)))])

(defn return
  "Create a generator that always returns `val`"
  [val]
  [:gen (fn [rand-seed size] val)])

;; Combinators
;; ---------------------------------------------------------------------------

(defn choose
  "Create a generator that returns numbers in the range
  `min-range` to `max-range`"
  [min-range max-range]
  (let [diff (Math/abs (long (- max-range min-range)))]
    [:gen (fn [^Random rand-seed _size]
            (if (zero? diff)
              min-range
              (+ (.nextInt rand-seed (inc diff)) min-range)))]))


(defn one-of
  "Create a generator that randomly chooses a value from the list of
  provided generators.

  Examples:

    (one-of [gen/int gen/boolean (gen/vector gen/int)])
  "
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
  "Create a generator that chooses a generator from `pairs` based on the
  provided likelihoods. The likelihood of a given generator being chosen is
  its likelihood divided by the sum of all likelihoods

  Examples:

    (gen/frequency [[5 gen/int] [3 (gen/vector gen/int)] [2 gen/boolean]])
  "
  [pairs]
  (let [total (apply + (clojure.core/map first pairs))]
    (bind (choose 1 total)
          (partial pick pairs))))

(defn elements
  "Create a generator that randomly chooses an element from `coll`.

  Examples:

    (gen/elements [:foo :bar :baz])"
  [coll]
  (fmap #(nth coll %)
        (choose 0 (dec (count coll)))))

(defn such-that
  "Create a generator that generates values from `gen` that satisfy predicate
  `f`. Care is needed to ensure there is a high chance `gen` will satisfy `f`,
  otherwise it will keep trying forever. Eventually we will add another
  generator combinator that only tries N times before giving up. In the Haskell
  version this is called `suchThatMaybe`.

  Examples:

    ;; generate non-empty vectors of integers
    (such-that not-empty (gen/vector gen/int))"
  [f gen]
  [:gen (fn [rand-seed size]
    (let [value (call-gen gen rand-seed size)]
      (if (f value)
        value
        (recur rand-seed size))))])

;; Generic generators and helpers
;; ---------------------------------------------------------------------------

(defn pair
  "Create a generator that generates two-vectors that generate a value
  from `a` and `b`.

  Examples:

    (pair gen/int gen/int)
  "
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

(def int
  "Generates a positive or negative integer bounded by the generator's
  `size` paramter."
  [:gen int-gen])

(def pos-int
  "Generate positive integers bounded by the generator's `size` paramter."
  (fmap #(Math/abs (long %)) int))
(def neg-int
  "Generate negative integers bounded by the generator's `size` paramter."
  (fmap (partial * -1) pos-int))

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
  "Create a generator that returns a vector, whose elements are chosen
  from the generators in the same position.

  Examples:

    (def t (tuple [gen/int gen/boolean]))
    (sample t)
    ;; => ([1 true] [2 true] [2 false] [1 false] [0 true] [-2 false] [-6 false]
    ;; =>  [3 true] [-4 false] [9 true]))
  "
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
  "Create a generator whose elements are chosen from `gen`. The count of the
  vector will be bounded by the `size` generator parameter."
  [gen]
  [:gen (fn [rand-seed size]
    (let [num-elements (Math/abs (long (call-gen int rand-seed size)))]
      (vec (repeatedly num-elements #(call-gen gen rand-seed size)))))])

(extend clojure.lang.IPersistentVector
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink (comp (partial clojure.core/map vec) shrink-seq)})

;; List
;; ---------------------------------------------------------------------------

(defn list
  "Like `vector`, but generators lists."
  [gen]
  [:gen (fn [rand-seed size]
    (let [num-elements (Math/abs (long (call-gen int rand-seed size)))]
      (into '() (repeatedly num-elements #(call-gen gen rand-seed size)))))])

(defn shrink-list
  [l]
  (clojure.core/map list* (shrink-seq l)))

(extend clojure.lang.PersistentList
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink shrink-list})

;; Bytes
;; ---------------------------------------------------------------------------

(def byte (fmap clojure.core/byte (choose 0 127)))

(def bytes (fmap clojure.core/byte-array (vector byte)))

(defn shrink-byte
  [b]
  (let [i (clojure.core/int b)]
    (clojure.core/map clojure.core/byte (shrink i))))

(defn shrink-bytes
  [bs]
  (let [vbs (vec bs)]
    (clojure.core/map clojure.core/byte-array (shrink vbs))))

(extend java.lang.Byte
  Shrink
  {:shrink shrink-byte})

(extend (Class/forName "[B")
  Shrink
  {:shrink shrink-bytes})

;; Map
;; ---------------------------------------------------------------------------

(defn map
  "Create a generator that generates maps, with keys chosen from
  `ken-gen` and values chosen from `val-gen`."
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

(def char
  "Generates character from 0-255"
  (fmap clojure.core/char (choose 0 255)))

(def char-ascii
  "Generate only ascii character"
  (fmap clojure.core/char (choose 32 126)))

(def char-alpha-numeric
  "Generate alpha-numeric characters"
  (fmap clojure.core/char
        (one-of [(choose 48 57)
                 (choose 65 90)
                 (choose 97 122)])))

(defn- stamp
  [^Character c]
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
            (for [^Character x [c] :while #(Character/isUpperCase ^Character %)]
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

(def string
  "Generate strings"
  [:gen string-gen])

(defn string-ascii-gen
  [rand-seed size]
  (clojure.string/join (repeatedly size #(call-gen char-ascii rand-seed size))))

(def string-ascii
  "Generate ascii strings"
  [:gen string-ascii-gen])

(defn string-alpha-numeric-gen
  [rand-seed size]
  (clojure.string/join (repeatedly size #(call-gen char-alpha-numeric rand-seed size))))

(def string-alpha-numeric
  "Generate alpha-numeric strings"
  [:gen string-alpha-numeric-gen])

(defn shrink-string
  [s]
  (clojure.core/map clojure.string/join (shrink-seq s)))

(extend java.lang.String
  Shrink
  {:shrink shrink-string})

;; Keyword
;; ---------------------------------------------------------------------------

(def keyword
  "Generate keywords"
  (->> string-alpha-numeric
       (such-that #(not= "" %))
       (fmap clojure.core/keyword)))

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
