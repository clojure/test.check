;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.generators
  (:import java.util.Random)
  (:refer-clojure :exclude [int vector list hash-map map keyword
                            char boolean byte bytes sequence
                            not-empty])
  (:require [clojure.core :as core]
            [clojure.test.check.rose-tree :as rose]))

;; Generic helpers
;; ---------------------------------------------------------------------------

(defn- sequence
  "Haskell type:
  Monad m => [m a] -> m [a]

  Specfically used here to turn a list of generators
  into a generator of a list."
  [bind-fn return-fn ms]
  (reduce (fn [acc elem]
            (bind-fn acc
                     (fn [xs]
                       (bind-fn elem
                                (fn [y]
                                  (return-fn (conj xs y)))))))
          (return-fn [])
          ms))


;; Gen
;; (internal functions)
;; ---------------------------------------------------------------------------

(defrecord Generator [gen])

(defn generator?
  "Test is `x` is a generator. Generators should be treated as opaque values."
  [x]
  (instance? Generator x))

(defn make-gen
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
      (let [inner (h rnd size)
            {result :gen} (k inner)]
        (result rnd size)))))

;; Exported generator functions
;; ---------------------------------------------------------------------------

(defn fmap
  [f gen]
  (gen-fmap (partial rose/fmap f) gen))


(defn return
  "Create a generator that always returns `value`,
  and never shrinks. You can think of this as
  the `constantly` of generators."
  [value]
  (gen-pure (rose/pure value)))

(defn bind-helper
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
  (gen-bind generator (bind-helper k)))

;; Helpers
;; ---------------------------------------------------------------------------

(defn random
  {:no-doc true}
  ([] (Random.))
  ([seed] (Random. seed)))

(defn make-size-range-seq
  {:no-doc true}
  [max-size]
  (cycle (range 0 max-size)))

(defn sample-seq
  "Return a sequence of realized values from `generator`."
  ([generator] (sample-seq generator 100))
  ([generator max-size]
   (let [r (random)
         size-seq (make-size-range-seq max-size)]
     (core/map (comp rose/root (partial call-gen generator r)) size-seq))))

(defn sample
  "Return a sequence of `num-samples` (default 10)
  realized values from `generator`."
  ([generator]
   (sample generator 10))
  ([generator num-samples]
   (take num-samples (sample-seq generator))))


;; Internal Helpers
;; ---------------------------------------------------------------------------

(defn- halfs
  [n]
  (take-while (partial not= 0) (iterate #(quot % 2) n)))

(defn- shrink-int
  [integer]
  (core/map (partial - integer) (halfs integer)))

(defn- int-rose-tree
  [value]
  [value (core/map int-rose-tree (shrink-int value))])

(defn- rand-range
  [^Random rnd lower upper]
  {:pre [(<= lower upper)]}
  (let [factor (.nextDouble rnd)]
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
  [n {gen :gen}]
  (make-gen
    (fn [rnd _size]
      (gen rnd n))))

(defn choose
  "Create a generator that returns numbers in the range
  `min-range` to `max-range`, inclusive."
  [lower upper]
  (make-gen
    (fn [^Random rnd _size]
      (let [value (rand-range rnd lower upper)]
        (rose/filter
          #(and (>= % lower) (<= % upper))
          [value (core/map int-rose-tree (shrink-int value))])))))

(defn one-of
  "Create a generator that randomly chooses a value from the list of
  provided generators. Shrinks toward choosing an earlier generator,
  as well as shrinking the value generated by the chosen generator.

  Examples:

      (one-of [gen/int gen/boolean (gen/vector gen/int)])

  "
  [generators]
  (bind (choose 0 (dec (count generators)))
        (partial nth generators)))

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
  (let [total (apply + (core/map first pairs))]
    (gen-bind (choose 1 total)
              #(pick pairs (rose/root %)))))

(defn elements
  "Create a generator that randomly chooses an element from `coll`.

  Examples:

      (gen/elements [:foo :bar :baz])
  "
  [coll]
  (when (empty? coll)
    (throw (ex-info "clojure.test.check.generators/elements called with empty collection!"
                    {:collection coll})))
  (gen-bind (choose 0 (dec (count coll)))
            #(gen-pure (rose/fmap (partial nth coll) %))))

(defn- such-that-helper
  [max-tries pred gen tries-left rand-seed size]
  (if (zero? tries-left)
    (throw (ex-info (str "Couldn't satisfy such-that predicate after "
                         max-tries " tries.") {}))
    (let [value (call-gen gen rand-seed size)]
      (if (pred (rose/root value))
        (rose/filter pred value)
        (recur max-tries pred gen (dec tries-left) rand-seed (inc size))))))

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
      (such-that not-empty (gen/vector gen/int))
  "
  ([pred gen]
   (such-that pred gen 10))
  ([pred gen max-tries]
   (make-gen
     (fn [rand-seed size]
       (such-that-helper max-tries pred gen max-tries rand-seed size)))))

(def not-empty
  "Modifies a generator so that it doesn't generate empty collections.

  Examples:

      ;; generate a vector of booleans, but never the empty vector
      (gen/not-empty (gen/vector gen/boolean))
  "
  (partial such-that core/not-empty))

(defn no-shrink
  "Create a new generator that is just like `gen`, except does not shrink
  at all. This can be useful when shrinking is taking a long time or is not
  applicable to the domain."
  [gen]
  (gen-bind gen
            (fn [[root _children]]
              (gen-pure
                [root []]))))

(defn shrink-2
  "Create a new generator like `gen`, but will consider nodes for shrinking
  even if their parent passes the test (up to one additional level)."
  [gen]
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
  (gen-bind (sequence gen-bind gen-pure generators)
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
  (fmap (partial * -1) nat))

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
   (gen-bind
     (sized #(choose 0 %))
     (fn [num-elements-rose]
       (gen-bind (sequence gen-bind gen-pure
                           (repeat (rose/root num-elements-rose)
                                   generator))
                 (fn [roses]
                   (gen-pure (rose/shrink core/vector
                                          roses)))))))
  ([generator num-elements]
   (apply tuple (repeat num-elements generator)))
  ([generator min-elements max-elements]
   (gen-bind
     (choose min-elements max-elements)
     (fn [num-elements-rose]
       (gen-bind (sequence gen-bind gen-pure
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
  "Like `vector`, but generators lists."
  [generator]
  (gen-bind (sized #(choose 0 %))
            (fn [num-elements-rose]
              (gen-bind (sequence gen-bind gen-pure
                                  (repeat (rose/root num-elements-rose)
                                          generator))
                        (fn [roses]
                          (gen-pure (rose/shrink core/list
                                                 roses)))))))

(def byte
  "Generates `java.lang.Byte`s, using the full byte-range."
  (fmap core/byte (choose Byte/MIN_VALUE Byte/MAX_VALUE)))

(def bytes
  "Generates byte-arrays."
  (fmap core/byte-array (vector byte)))

(defn map
  "Create a generator that generates maps, with keys chosen from
  `key-gen` and values chosen from `val-gen`."
  [key-gen val-gen]
  (let [input (vector (tuple key-gen val-gen))]
    (fmap (partial into {}) input)))

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
    (fmap (partial zipmap ks)
          (apply tuple vs))))

(def char
  "Generates character from 0-255."
  (fmap core/char (choose 0 255)))

(def char-ascii
  "Generate only ascii character."
  (fmap core/char (choose 32 126)))

(def char-alpha-numeric
  "Generate alpha-numeric characters."
  (fmap core/char
        (one-of [(choose 48 57)
                 (choose 65 90)
                 (choose 97 122)])))

(def char-alpha
  "Generate alpha characters."
  (fmap core/char
        (one-of [(choose 65 90)
                 (choose 97 122)])))

(def char-symbol-special
  "Generate non-alphanumeric characters that can be in a symbol."
  (elements [\* \+ \! \- \_ \?]))

(def char-keyword-rest
  "Generate characters that can be the char following first of a keyword."
  (frequency [[2 char-alpha-numeric]
              [1 char-symbol-special]]))

(def char-keyword-first
  "Generate characters that can be the first char of a keyword."
  (frequency [[2 char-alpha]
              [1 char-symbol-special]]))

(def string
  "Generate strings. May generate unprintable characters."
  (fmap clojure.string/join (vector char)))

(def string-ascii
  "Generate ascii strings."
  (fmap clojure.string/join (vector char-ascii)))

(def string-alpha-numeric
  "Generate alpha-numeric strings."
  (fmap clojure.string/join (vector char-alpha-numeric)))

(def keyword
  "Generate keywords."
  (->> (tuple char-keyword-first (vector char-keyword-rest))
       (fmap (fn [[c cs]] (core/keyword (clojure.string/join (cons c cs)))))))

(def ratio
  "Generates a `clojure.lang.Ratio`. Shrinks toward 0. Not all values generated
  will be ratios, as many values returned by `/` are not ratios."
  (fmap
    (fn [[a b]] (/ a b))
    (tuple int
           (such-that (complement zero?) int))))

(def simple-type
  (one-of [int char string ratio boolean keyword]))

(def simple-type-printable
  (one-of [int char-ascii string-ascii ratio boolean keyword]))

(defn container-type
  [inner-type]
  (one-of [(vector inner-type)
           (list inner-type)
           (map inner-type inner-type)]))

(defn sized-container
  {:no-doc true}
  [inner-type]
  (fn [size]
    (if (zero? size)
      inner-type
      (one-of [inner-type
               (container-type (resize (quot size 2) (sized (sized-container inner-type))))]))))

(def any
  "A recursive generator that will generate many different, often nested, values"
  (sized (sized-container simple-type)))

(def any-printable
  "Like any, but avoids characters that the shell will interpret as actions,
  like 7 and 14 (bell and alternate character set command)"
  (sized (sized-container simple-type-printable)))
