(ns simple-check.generators
  (:import java.util.Random)
  (:refer-clojure :exclude [int vector list hash-map map keyword
                            char boolean byte bytes sequence
                            not-empty]))

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

;; Rose tree
;; ---------------------------------------------------------------------------
;;
;; A Rose tree is an n-ary tree.
;; These are all internal functions

(defn join-rose
  "Turn a tree of trees into a single tree. Does this by concatenating
  children of the inner and outer trees."
  {:no-doc true}
  [[[inner-root inner-children] children]]
  [inner-root (concat (clojure.core/map join-rose children)
                      inner-children)])

(defn rose-root
  "Returns the root of a Rose tree."
  {:no-doc true}
  [[root _children]]
  root)

(defn rose-children
  "Returns the children of the root of the Rose tree."
  {:no-doc true}
  [[_root children]]
  children)

(defn rose-pure
  "Puts a value `x` into a Rose tree, with no children."
  {:no-doc true}
  [x]
  [x []])

(defn rose-fmap
  "Applies functions `f` to all values in the tree."
  {:no-doc true}
  [f [root children]]
  [(f root) (clojure.core/map (partial rose-fmap f) children)])

(defn rose-bind
  "Takes a Rose tree (m) and a function (k) from
  values to Rose tree and returns a new Rose tree.
  This is the monadic bind (>>=) for Rose trees."
  {:no-doc true}
  [m k]
  (join-rose (rose-fmap k m)))

(defn rose-filter
  "Returns a new Rose tree whose values pass `pred`. Values who
  do not pass `pred` have their children cut out as well.
  Takes a list of roses, not a rose"
  {:no-doc true}
  [pred [root children]]
  [root (clojure.core/map (partial rose-filter pred)
                          (clojure.core/filter (comp pred rose-root) children))])
(defn rose-permutations
  "Create a seq of vectors, where each rose in turn, has been replaced
  by its children."
  {:no-doc true}
  [roses]
  (apply concat
         (for [[rose index]
               (clojure.core/map clojure.core/vector roses (range))]
           (for [child (rose-children rose)] (assoc roses index child)))))

(defn zip-rose
  "Apply `f` to the sequence of Rose trees `roses`."
  {:no-doc true}
  [f roses]
  [(apply f (clojure.core/map rose-root roses))
   (clojure.core/map (partial zip-rose f)
                     (rose-permutations roses))])

(defn remove-roses
  {:no-doc true}
  [roses]
  (concat
    [(rest roses)]
    [(drop-last roses)]
    (rose-permutations (vec roses))))

(defn shrink-rose
  {:no-doc true}
  [f roses]
  (if (seq roses)
    [(apply f (clojure.core/map rose-root roses))
     (clojure.core/map (partial shrink-rose f) (remove-roses roses))]
    [(f) []]))

;; Gen
;; (internal functions)
;; ---------------------------------------------------------------------------

(defn make-gen
  ([generator-fn]
   {:gen generator-fn}))

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
  (gen-fmap (partial rose-fmap f) gen))


(defn return
  "Create a generator that always returns `value`,
  and never shrinks."
  [value]
  (gen-pure (rose-pure value)))

(defn bind-helper
  [k]
  (fn [rose]
    (gen-fmap join-rose
              (make-gen
                (fn [rnd size]
                  (rose-fmap #(call-gen % rnd size)
                             (rose-fmap k rose)))))))

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

  Aside: technically this is not bind for a particular monad, since
  we're dealing with two nested monads (Gen and RoseTree). The name
  is kept for backward compatibility.
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
     (clojure.core/map (comp rose-root (partial call-gen generator r)) size-seq))))

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
  (clojure.core/map (partial - integer) (halfs integer)))

(defn- int-rose-tree
  [value]
  [value (clojure.core/map int-rose-tree (shrink-int value))])

(defn- rand-range
  [^Random rnd lower upper]
  (let [diff (Math/abs (long (- upper lower)))]
    (if (zero? diff)
      lower
      (+ (.nextInt rnd (inc diff)) lower))))

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
  `min-range` to `max-range`."
  [lower upper]
  (make-gen
    (fn [^Random rnd _size]
      (let [value (rand-range rnd lower upper)]
        (rose-filter
          #(>= % lower)
          [value (clojure.core/map int-rose-tree (shrink-int value))])))))

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
  (let [total (apply + (clojure.core/map first pairs))]
    (gen-bind (choose 1 total)
              #(pick pairs (rose-root %)))))

(defn elements
  "Create a generator that randomly chooses an element from `coll`.

  Examples:

      (gen/elements [:foo :bar :baz])
  "
  [coll]
  (gen-bind (choose 0 (dec (count coll)))
            #(gen-pure (rose-fmap (partial nth coll) %))))

(defn such-that
  "Create a generator that generates values from `gen` that satisfy predicate
  `f`. Care is needed to ensure there is a high chance `gen` will satisfy `f`,
  otherwise it will keep trying forever. Eventually we will add another
  generator combinator that only tries N times before giving up. In the Haskell
  version this is called `suchThatMaybe`.

  Examples:

      ;; generate non-empty vectors of integers
      (such-that not-empty (gen/vector gen/int))
  "
  [pred gen]
  (make-gen
    (fn [rand-seed size]
      (let [value (call-gen gen rand-seed size)]
        (if (pred (rose-root value))
          (rose-filter pred value)
          (recur rand-seed (inc size)))))))

(def not-empty
  "Modifies a generator so that it doesn't generate empty collections."
  (partial such-that clojure.core/not-empty))

(def boolean
  (elements [false true]))

(defn tuple
  "Create a generator that returns a vector, whose elements are chosen
  from the generators in the same position.

  Examples:

      (def t (tuple gen/int gen/boolean))
      (sample t)
      ;; => ([1 true] [2 true] [2 false] [1 false] [0 true] [-2 false] [-6 false]
      ;; =>  [3 true] [-4 false] [9 true]))
  "
  [& generators]
  (gen-bind (sequence gen-bind gen-pure generators)
            (fn [roses]
              (gen-pure (zip-rose clojure.core/vector roses)))))

(def int
  "Generates a positive or negative integer bounded by the generator's
  `size` parameter.
  (Really returns a long)"
  (sized (fn [size] (choose (- size) size))))

(def nat
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
                           (repeat (rose-root num-elements-rose)
                                   generator))
                 (fn [roses]
                   (gen-pure (shrink-rose clojure.core/vector
                                          roses)))))))
  ([generator num-elements]
   (apply tuple (repeat num-elements generator)))
  ([generator min-elements max-elements]
   (gen-bind
     (choose min-elements max-elements)
     (fn [num-elements-rose]
       (gen-bind (sequence gen-bind gen-pure
                           (repeat (rose-root num-elements-rose)
                                   generator))
                 (fn [roses]
                   (gen-bind
                     (gen-pure (shrink-rose clojure.core/vector
                                            roses))
                     (fn [rose]
                       (gen-pure (rose-filter
                                   (fn [v] (and (>= (count v) min-elements)
                                                (<= (count v) max-elements))) rose))))))))))

(defn list
  "Like `vector`, but generators lists."
  [generator]
  (gen-bind (sized #(choose 0 %))
            (fn [num-elements-rose]
              (gen-bind (sequence gen-bind gen-pure
                                  (repeat (rose-root num-elements-rose)
                                          generator))
                        (fn [roses]
                          (gen-pure (shrink-rose clojure.core/list
                                                 roses)))))))

(def byte (fmap clojure.core/byte (choose Byte/MIN_VALUE Byte/MAX_VALUE)))

(def bytes (fmap clojure.core/byte-array (vector byte)))

(defn map
  "Create a generator that generates maps, with keys chosen from
  `ken-gen` and values chosen from `val-gen`."
  [key-gen val-gen]
  (let [input (vector (tuple key-gen val-gen))]
    (fmap (partial into {}) input)))

(defn hash-map 
  "Like clojure.core/hash-map, except the values are generators.
   Returns a generator that makes maps with the supplied keys and 
   values generated using the supplied generators."
  [& kvs]
  (assert (even? (count kvs)))
  (let [ks (take-nth 2 kvs)
        vs (take-nth 2 (rest kvs))]
    (fmap (partial zipmap ks)
          (apply tuple vs))))

(def char
  "Generates character from 0-255."
  (fmap clojure.core/char (choose 0 255)))

(def char-ascii
  "Generate only ascii character."
  (fmap clojure.core/char (choose 32 126)))

(def char-alpha-numeric
  "Generate alpha-numeric characters."
  (fmap clojure.core/char
        (one-of [(choose 48 57)
                 (choose 65 90)
                 (choose 97 122)])))

(def string
  "Generate strings."
  (fmap clojure.string/join (vector char)))

(def string-ascii
  "Generate ascii strings."
  (fmap clojure.string/join (vector char-ascii)))

(def string-alpha-numeric
  "Generate alpha-numeric strings."
  (fmap clojure.string/join (vector char-alpha-numeric)))

(def keyword
  "Generate keywords."
  (->> string-alpha-numeric
    (such-that #(not= "" %))
    (fmap clojure.core/keyword)))

(def ratio
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
