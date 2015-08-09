;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.generators
  (:refer-clojure :exclude [int vector list hash-map map keyword
                            char boolean byte bytes sequence
                            shuffle not-empty symbol namespace])
  (:require [cljs.core :as core]
            [clojure.test.check.random :as random]
            [clojure.test.check.rose-tree :as rose]
            [goog.string :as gstring]
            [clojure.string])
  (:import [goog.testing PseudoRandom]))


;; Gen
;; (internal functions)
;; ---------------------------------------------------------------------------

(defrecord Generator [gen])

(defn generator?
  "Test if `x` is a generator. Generators should be treated as opaque values."
  [x]
  (instance? Generator x))

(defn- make-gen
  [generator-fn]
  (Generator. generator-fn))

(defn call-gen
  {:no-doc true}
  [{generator-fn :gen} rnd size]
  (generator-fn rnd size))

(defn gen-pure
  {:no-doc true}
  [value]
  (make-gen
    (fn [rnd size]
      value)))

(defn gen-fmap
  {:no-doc true}
  [k {h :gen}]
  (make-gen
    (fn [rnd size]
      (k (h rnd size)))))

(defn gen-bind
  {:no-doc true}
  [{h :gen} k]
  (make-gen
    (fn [rnd size]
      (let [[r1 r2] (random/split rnd)
            inner (h r1 size)
            {result :gen} (k inner)]
        (result r2 size)))))

(defn lazy-random-states
  "Given a random number generator, returns an infinite lazy sequence
  of random number generators."
  [rr]
  (lazy-seq
   (let [[r1 r2] (random/split rr)]
     (cons r1
           (lazy-random-states r2)))))

(defn- gen-seq->seq-gen
  "Takes a sequence of generators and returns a generator of sequences (er, vectors)."
  [gens]
  (make-gen
   (fn [rnd size]
     (mapv #(call-gen % %2 size) gens (random/split-n rnd (count gens))))))

;; Exported generator functions
;; ---------------------------------------------------------------------------

(defn fmap
  [f gen]
  (assert (generator? gen) "Second arg to fmap must be a generator")
  (gen-fmap #(rose/fmap f %) gen))


(defn return
  "Create a generator that always returns `value`,
  and never shrinks. You can think of this as
  the `constantly` of generators."
  [value]
  (gen-pure (rose/pure value)))

(defn- bind-helper
  [k]
  (fn [rose]
    (gen-fmap rose/join
              (make-gen
                (fn [rnd size]
                  (rose/fmap #(call-gen % rnd size)
                             (rose/fmap k rose)))))))

(defn bind
  "Create a new generator that passes the result of `gen` into function
  `k`. `k` should return a new generator. This allows you to create new
  generators that depend on the value of other generators. For example,
  to create a generator which first generates a vector of integers, and
  then chooses a random element from that vector:

      (gen/bind (gen/such-that not-empty (gen/vector gen/int))
                ;; this function takes a realized vector,
                ;; and then returns a new generator which
                ;; chooses a random element from it
                gen/elements)

  "
  [generator k]
  (assert (generator? generator) "First arg to bind must be a generator")
  (gen-bind generator (bind-helper k)))

;; Helpers
;; ---------------------------------------------------------------------------

(defn make-size-range-seq
  {:no-doc true}
  [max-size]
  (cycle (range 0 max-size)))

(defn sample-seq
  "Return a sequence of realized values from `generator`."
  ([generator] (sample-seq generator 100))
  ([generator max-size]
   (let [r (random/make-random)
         size-seq (make-size-range-seq max-size)]
     (core/map #(rose/root (call-gen generator %1 %2))
               (lazy-random-states r)
               size-seq))))

(defn sample
  "Return a sequence of `num-samples` (default 10)
  realized values from `generator`."
  ([generator]
   (sample generator 10))
  ([generator num-samples]
   (assert (generator? generator) "First arg to sample must be a generator")
   (take num-samples (sample-seq generator))))


(defn generate
  "Returns a single sample value from the generator, using a default
  size of 30."
  ([generator]
     (generate generator 30))
  ([generator size]
     (let [rng (random/make-random)]
       (rose/root (call-gen generator rng size)))))

;; Internal Helpers
;; ---------------------------------------------------------------------------

(defn- halfs
  [n]
  (take-while #(not= 0 %) (iterate #(quot % 2) n)))

(defn- shrink-int
  [integer]
  (core/map #(- integer %) (halfs integer)))

(defn- int-rose-tree
  [value]
  (rose/make-rose value (core/map int-rose-tree (shrink-int value))))

(defn- rand-range
  [rnd lower upper]
  {:pre [(<= lower upper)]}
  (let [factor (random/rand-double rnd)]
    (long (Math/floor (+ lower (- (* factor (+ 1.0 upper))
                                  (* factor lower)))))))

(defn sized
  "Create a generator that depends on the size parameter.
  `sized-gen` is a function that takes an integer and returns
  a generator."
  [sized-gen]
  (make-gen
    (fn [rnd size]
      (let [sized-gen (sized-gen size)]
        (call-gen sized-gen rnd size)))))

;; Combinators and helpers
;; ---------------------------------------------------------------------------

(defn resize
  "Create a new generator with `size` always bound to `n`."
  [n generator]
  (assert (generator? generator) "Second arg to resize must be a generator")
  (let [{:keys [gen]} generator]
    (make-gen
     (fn [rnd _size]
       (gen rnd n)))))

(defn scale
  "Create a new generator that modifies the size parameter by the given function. Intended to
   support generators with sizes that need to grow at different rates compared to the normal
   linear scaling."
  ([f generator]
    (sized (fn [n] (resize (f n) generator)))))

(defn choose
  "Create a generator that returns numbers in the range
  `min-range` to `max-range`, inclusive."
  [lower upper]
  (make-gen
    (fn [rnd _size]
      (let [value (rand-range rnd lower upper)]
        (rose/filter
          #(and (>= % lower) (<= % upper))
          (int-rose-tree value))))))

(defn one-of
  "Create a generator that randomly chooses a value from the list of
  provided generators. Shrinks toward choosing an earlier generator,
  as well as shrinking the value generated by the chosen generator.

  Examples:

      (one-of [gen/int gen/boolean (gen/vector gen/int)])

  "
  [generators]
  (assert (every? generator? generators)
          "Arg to one-of must be a collection of generators")
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
  (assert (every? (fn [[x g]] (and (number? x) (generator? g)))
                  pairs)
          "Arg to frequency must be a list of [num generator] pairs")
  (let [total (apply + (core/map first pairs))]
    (gen-bind (choose 1 total)
              #(pick pairs (rose/root %)))))

(defn elements
  "Create a generator that randomly chooses an element from `coll`.

  Examples:

      (gen/elements [:foo :bar :baz])
  "
  [coll]
  (assert (seq coll) "elements cannot be called with an empty collection")
  (let [v (vec coll)]
    (gen-bind (choose 0 (dec (count v)))
              #(gen-pure (rose/fmap v %)))))

(defn- such-that-helper
  [max-tries pred gen tries-left rng size]
  (if (zero? tries-left)
    (throw (ex-info (str "Couldn't satisfy such-that predicate after "
                         max-tries " tries.") {}))
    (let [[r1 r2] (random/split rng)
          value (call-gen gen r1 size)]
      (if (pred (rose/root value))
        (rose/filter pred value)
        (recur max-tries pred gen (dec tries-left) r2 (inc size))))))

(defn such-that
  "Create a generator that generates values from `gen` that satisfy predicate
  `pred`. Care is needed to ensure there is a high chance `gen` will satisfy
  `pred`. By default, `such-that` will try 10 times to generate a value that
  satisfies the predicate. If no value passes this predicate after this number
  of iterations, a runtime exception will be throw. You can pass an optional
  third argument to change the number of times tried. Note also that each
  time such-that retries, it will increase the size parameter.

  Examples:

      ;; generate non-empty vectors of integers
      ;; (note, gen/not-empty does exactly this)
      (gen/such-that not-empty (gen/vector gen/int))
  "
  ([pred gen]
   (such-that pred gen 10))
  ([pred gen max-tries]
   (assert (generator? gen) "Second arg to such-that must be a generator")
   (make-gen
     (fn [rand-seed size]
       (such-that-helper max-tries pred gen max-tries rand-seed size)))))

(defn not-empty
  "Modifies a generator so that it doesn't generate empty collections.

  Examples:

      ;; generate a vector of booleans, but never the empty vector
      (gen/not-empty (gen/vector gen/boolean))
  "
  [gen]
  (assert (generator? gen) "Arg to not-empty must be a generator")
  (such-that core/not-empty gen))

(defn no-shrink
  "Create a new generator that is just like `gen`, except does not shrink
  at all. This can be useful when shrinking is taking a long time or is not
  applicable to the domain."
  [gen]
  (assert (generator? gen) "Arg to no-shrink must be a generator")
  (gen-bind gen
            (fn [rose]
              (gen-pure (rose/make-rose (rose/root rose) [])))))

(defn shrink-2
  "Create a new generator like `gen`, but will consider nodes for shrinking
  even if their parent passes the test (up to one additional level)."
  [gen]
  (assert (generator? gen) "Arg to shrink-2 must be a generator")
  (gen-bind gen (comp gen-pure rose/collapse)))

(def boolean
  "Generates one of `true` or `false`. Shrinks to `false`."
  (elements [false true]))

(defn tuple
  "Create a generator that returns a vector, whose elements are chosen
  from the generators in the same position. The individual elements shrink
  according to their generator, but the value will never shrink in count.

  Examples:

      (def t (tuple gen/int gen/boolean))
      (sample t)
      ;; => ([1 true] [2 true] [2 false] [1 false] [0 true] [-2 false] [-6 false]
      ;; =>  [3 true] [-4 false] [9 true]))
  "
  [& generators]
  (assert (every? generator? generators)
          "Args to tuple must be generators")
  (gen-bind (gen-seq->seq-gen generators)
            (fn [roses]
              (gen-pure (rose/zip core/vector roses)))))

(def int
  "Generates a positive or negative integer bounded by the generator's
  `size` parameter.
  (Really returns a long)"
  (sized (fn [size] (choose (- size) size))))

(def nat
  "Generates natural numbers, starting at zero. Shrinks to zero."
  (fmap #(Math/abs (long %)) int))

(def pos-int
  "Generate positive integers bounded by the generator's `size` parameter."
  nat)

(def neg-int
  "Generate negative integers bounded by the generator's `size` parameter."
  (fmap #(* -1 %) nat))

(def s-pos-int
  "Generate strictly positive integers bounded by the generator's `size`
   parameter."
  (fmap inc nat))

(def s-neg-int
  "Generate strictly negative integers bounded by the generator's `size`
   parameter."
  (fmap dec neg-int))

(defn vector
  "Create a generator whose elements are chosen from `gen`. The count of the
  vector will be bounded by the `size` generator parameter."
  ([generator]
   (assert (generator? generator) "Arg to vector must be a generator")
   (gen-bind
     (sized #(choose 0 %))
     (fn [num-elements-rose]
       (gen-bind (gen-seq->seq-gen
                  (repeat (rose/root num-elements-rose)
                          generator))
                 (fn [roses]
                   (gen-pure (rose/shrink core/vector
                                          roses)))))))
  ([generator num-elements]
   (assert (generator? generator) "First arg to vector must be a generator")
   (apply tuple (repeat num-elements generator)))
  ([generator min-elements max-elements]
   (assert (generator? generator) "First arg to vector must be a generator")
   (gen-bind
     (choose min-elements max-elements)
     (fn [num-elements-rose]
       (gen-bind (gen-seq->seq-gen
                  (repeat (rose/root num-elements-rose)
                          generator))
                 (fn [roses]
                   (gen-bind
                     (gen-pure (rose/shrink core/vector
                                            roses))
                     (fn [rose]
                       (gen-pure (rose/filter
                                   (fn [v] (and (>= (count v) min-elements)
                                                (<= (count v) max-elements))) rose))))))))))

(defn list
  "Like `vector`, but generates lists."
  [generator]
  (assert (generator? generator) "First arg to list must be a generator")
  (gen-bind (sized #(choose 0 %))
            (fn [num-elements-rose]
              (gen-bind (gen-seq->seq-gen
                         (repeat (rose/root num-elements-rose)
                                 generator))
                        (fn [roses]
                          (gen-pure (rose/shrink core/list
                                                 roses)))))))

(defn- swap
  [coll [i1 i2]]
  (assoc coll i2 (coll i1) i1 (coll i2)))

(defn
  ^{:added "0.6.0"}
  shuffle
  "Create a generator that generates random permutations of `coll`. Shrinks
  toward the original collection: `coll`. `coll` will be turned into a vector,
  if it's not already."
  [coll]
  (let [index-gen (choose 0 (dec (count coll)))]
    (fmap #(reduce swap (vec coll) %)
          ;; a vector of swap instructions, with count between
          ;; zero and 2 * count. This means that the average number
          ;; of instructions is count, which should provide sufficient
          ;; (though perhaps not 'perfect') shuffling. This still gives us
          ;; nice, relatively quick shrinks.
          (vector (tuple index-gen index-gen) 0 (* 2 (count coll))))))

;; NOTE: Comment out for now - David
;;
;; (def byte
;;   "Generates `java.lang.Byte`s, using the full byte-range."
;;   (fmap core/byte (choose Byte/MIN_VALUE Byte/MAX_VALUE)))

;; (def bytes
;;   "Generates byte-arrays."
;;   (fmap core/byte-array (vector byte)))

(defn map
  "Create a generator that generates maps, with keys chosen from
  `key-gen` and values chosen from `val-gen`."
  [key-gen val-gen]
  (let [input (vector (tuple key-gen val-gen))]
    (fmap #(into {} %) input)))

(defn hash-map
  "Like clojure.core/hash-map, except the values are generators.
   Returns a generator that makes maps with the supplied keys and
   values generated using the supplied generators.

  Examples:

    (gen/hash-map :a gen/boolean :b gen/nat)
  "
  [& kvs]
  (assert (even? (count kvs)))
  (let [ks (take-nth 2 kvs)
        vs (take-nth 2 (rest kvs))]
    (assert (every? generator? vs)
            "Value args to hash-map must be generators")
    (fmap #(zipmap ks %)
          (apply tuple vs))))

(def char
  "Generates character from 0-255."
  (fmap core/char (choose 0 255)))

(def char-ascii
  "Generate only ascii character."
  (fmap core/char (choose 32 126)))

(def char-alphanumeric
  "Generate alphanumeric characters."
  (fmap core/char
        (one-of [(choose 48 57)
                 (choose 65 90)
                 (choose 97 122)])))

(def ^{:deprecated "0.6.0"}
  char-alpha-numeric
  "Deprecated - use char-alphanumeric instead.

  Generate alphanumeric characters."
  char-alphanumeric)

(def char-alpha
  "Generate alpha characters."
  (fmap core/char
        (one-of [(choose 65 90)
                 (choose 97 122)])))

(def ^{:private true} char-symbol-special
  "Generate non-alphanumeric characters that can be in a symbol."
  (elements [\* \+ \! \- \_ \?]))

(def ^{:private true} char-keyword-rest
  "Generate characters that can be the char following first of a keyword."
  (frequency [[2 char-alphanumeric]
              [1 char-symbol-special]]))

(def ^{:private true} char-keyword-first
  "Generate characters that can be the first char of a keyword."
  (frequency [[2 char-alpha]
              [1 char-symbol-special]]))

(def string
  "Generate strings. May generate unprintable characters."
  (fmap clojure.string/join (vector char)))

(def string-ascii
  "Generate ascii strings."
  (fmap clojure.string/join (vector char-ascii)))

(def string-alphanumeric
  "Generate alphanumeric strings."
  (fmap clojure.string/join (vector char-alphanumeric)))

(def ^{:deprecated "0.6.0"}
  string-alpha-numeric
  "Deprecated - use string-alphanumeric instead.

  Generate alphanumeric strings."
  string-alphanumeric)

(defn- +-or---digit?
  "Returns true if c is \\+ or \\- and d is non-nil and a digit.

  Symbols that start with +3 or -2 are not readable because they look
  like numbers."
  [c d]
  (core/boolean (and d
                     (or (identical? \+ c)
                         (identical? \- c))
                     (gstring/isNumeric d))))

(def ^{:private true} namespace-segment
  "Generate the segment of a namespace."
  (->> (tuple char-keyword-first (vector char-keyword-rest))
       (such-that (fn [[c [d]]] (not (+-or---digit? c d))))
       (fmap (fn [[c cs]] (clojure.string/join (cons c cs))))))

(def ^{:private true} namespace
  "Generate a namespace (or nil for no namespace)."
  (->> (vector namespace-segment)
       (fmap (fn [v] (when (seq v)
                       (clojure.string/join "." v))))))

(def ^{:private true} keyword-segment-rest
  "Generate segments of a keyword (between \\:)"
  (->> (tuple char-keyword-rest (vector char-keyword-rest))
       (fmap (fn [[c cs]] (clojure.string/join (cons c cs))))))

(def ^{:private true} keyword-segment-first
  "Generate segments of a keyword that can be first (between \\:)"
  (->> (tuple char-keyword-first (vector char-keyword-rest))
       (fmap (fn [[c cs]] (clojure.string/join (cons c cs))))))

(def keyword
  "Generate keywords without namespaces."
  (->> (tuple keyword-segment-first (vector keyword-segment-rest))
       (fmap (fn [[c cs]]
               (core/keyword (clojure.string/join ":" (cons c cs)))))))

(def
  ^{:added "0.5.9"}
  keyword-ns
  "Generate keywords with optional namespaces."
  (->> (tuple namespace char-keyword-first (vector char-keyword-rest))
       (fmap (fn [[ns c cs]]
               (core/keyword ns (clojure.string/join (cons c cs)))))))

(def ^{:private true} char-symbol-first
  (frequency [[10 char-alpha]
              [5 char-symbol-special]
              [1 (return \.)]]))

(def ^{:private true} char-symbol-rest
  (frequency [[10 char-alphanumeric]
              [5 char-symbol-special]
              [1 (return \.)]]))

(def symbol
  "Generate symbols without namespaces."
  (frequency [[100 (->> (tuple char-symbol-first (vector char-symbol-rest))
                        (such-that (fn [[c [d]]] (not (+-or---digit? c d))))
                        (fmap (fn [[c cs]] (core/symbol (clojure.string/join (cons c cs))))))]
              [1 (return '/)]]))

(def
  ^{:added "0.5.9"}
  symbol-ns
  "Generate symbols with optional namespaces."
  (frequency [[100 (->> (tuple namespace char-symbol-first (vector char-symbol-rest))
                        (such-that (fn [[_ c [d]]] (not (+-or---digit? c d))))
                        (fmap (fn [[ns c cs]] (core/symbol ns (clojure.string/join (cons c cs))))))]
              [1 (return '/)]]))

(def ratio
  "Generates a `clojure.lang.Ratio`. Shrinks toward 0. Not all values generated
  will be ratios, as many values returned by `/` are not ratios."
  (fmap
    (fn [[a b]] (/ a b))
    (tuple int
           (such-that (complement zero?) int))))

(def simple-type
  (one-of [int char string ratio boolean keyword keyword-ns symbol symbol-ns]))

(def simple-type-printable
  (one-of [int char-ascii string-ascii ratio boolean keyword keyword-ns symbol symbol-ns]))

(defn container-type
  [inner-type]
  (one-of [(vector inner-type)
           (list inner-type)
           (map inner-type inner-type)]))

(defn- recursive-helper
  [container-gen-fn scalar-gen scalar-size children-size height]
  (if (zero? height)
    (resize scalar-size scalar-gen)
    (resize children-size
            (container-gen-fn
              (recursive-helper
                container-gen-fn scalar-gen
                scalar-size children-size (dec height))))))

(defn
  ^{:added "0.5.9"}
  recursive-gen
  "This is a helper for writing recursive (tree-shaped) generators. The first
  argument should be a function that takes a generator as an argument, and
  produces another generator that 'contains' that generator. The vector function
  in this namespace is a simple example. The second argument is a scalar
  generator, like boolean. For example, to produce a tree of booleans:

    (gen/recursive-gen gen/vector gen/boolean)

  Vectors or maps either recurring or containing booleans or integers:

    (gen/recursive-gen (fn [inner] (gen/one-of [(gen/vector inner)
                                                (gen/map inner inner)]))
                       (gen/one-of [gen/boolean gen/int]))
  "
  [container-gen-fn scalar-gen]
  (assert (generator? scalar-gen)
          "Second arg to recursive-gen must be a generator")
  (sized (fn [size]
           (bind (choose 1 5)
                 (fn [height] (let [children-size (Math/pow size (/ 1 height))]
                                (recursive-helper container-gen-fn scalar-gen size
                                                  children-size height)))))))

(def any
  "A recursive generator that will generate many different, often nested, values"
  (recursive-gen container-type simple-type))

(def any-printable
  "Like any, but avoids characters that the shell will interpret as actions,
  like 7 and 14 (bell and alternate character set command)"
  (recursive-gen container-type simple-type-printable))
