;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.test
  #?(:cljs
     (:refer-clojure :exclude [infinite?]))
  (:require #?(:cljs
               [cljs.test :as test :refer-macros [deftest testing is]])
            #?(:clj
               [clojure.test :refer :all])
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.rose-tree :as rose]
            [clojure.test.check.random :as random]
            [clojure.test.check.results :as results]
            [clojure.test.check.clojure-test :as ct :refer [defspec]]
            #?(:clj [clojure.test.check.test-specs :as specs])
            #?(:cljs [clojure.test.check.random.longs :as rl])
            #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn :refer [read-string]])))

(def gen-seed
  (let [gen-int (gen/choose 0 0x100000000)]
    (gen/fmap (fn [[s1 s2]]
                (bit-or s1 (bit-shift-left s2 32)))
              (gen/tuple gen-int gen-int))))

(deftest generators-are-generators
  (testing "generator? returns true when called with a generator"
    (is (gen/generator? gen/int))
    (is (gen/generator? (gen/vector gen/int)))
    (is (gen/generator? (gen/return 5)))))

(deftest values-are-not-generators
  (testing "generator? returns false when called with a value"
    (is (not (gen/generator? 5)))
    (is (not (gen/generator? int)))
    (is (not (gen/generator? [1 2 3])))))

;; plus and 0 form a monoid
;; ---------------------------------------------------------------------------

(defn passes-monoid-properties
  [a b c]
  (and (= (+ 0 a) a)
       (= (+ a 0) a)
       (= (+ a (+ b c)) (+ (+ a b) c))))

(deftest plus-and-0-are-a-monoid
  (testing "+ and 0 form a monoid"
    (is (let [p (prop/for-all* [gen/int gen/int gen/int] passes-monoid-properties)]
          (:result
           (tc/quick-check 1000 p)))))
  ;; NOTE: no ratios in ClojureScript - David
  #?(:clj
     (testing "with ratios as well"
       (is (let [p (prop/for-all* [gen/ratio gen/ratio gen/ratio] passes-monoid-properties)]
             (:result
              (tc/quick-check 1000 p)))))))

;; reverse
;; ---------------------------------------------------------------------------

(defn reverse-equal?-helper
  [l]
  (let [r (vec (reverse l))]
    (and (= (count l) (count r))
         (= (seq l) (rseq r)))))

(deftest reverse-equal?
  (testing "For all vectors L, reverse(reverse(L)) == L"
    (is (let [p (prop/for-all* [(gen/vector gen/int)] reverse-equal?-helper)]
          (:result (tc/quick-check 1000 p))))))

;; failing reverse
;; ---------------------------------------------------------------------------

(deftest bad-reverse-test
  (testing "For all vectors L, L == reverse(L). Not true"
    (is (false?
         (let [p (prop/for-all* [(gen/vector gen/int)] #(= (reverse %) %))]
           (:result (tc/quick-check 1000 p)))))))

;; failing element remove
;; ---------------------------------------------------------------------------

(defn first-is-gone
  [l]
  (not (some #{(first l)} (rest l))))

(deftest bad-remove
  (testing "For all vectors L, if we remove the first element E, E should not
           longer be in the list. (This is a false assumption)"
    (is (false?
         (let [p (prop/for-all* [(gen/vector gen/int)] first-is-gone)]
           (:result (tc/quick-check 1000 p)))))))

;; exceptions shrink and return as result
;; ---------------------------------------------------------------------------

(def exception (#?(:clj Exception. :cljs js/Error.) "I get caught"))

(defn exception-thrower
  [& args]
  (throw exception))

(deftest exceptions-are-caught
  (testing "Exceptions during testing are caught. They're also shrunk as long
           as they continue to throw."
    (is (= [exception [0]]
           (let [result
                 (tc/quick-check
                  1000 (prop/for-all* [gen/int] exception-thrower))]
             [(::prop/error (:result-data result))
              (get-in result [:shrunk :smallest])])))))

;; result-data
;; ---------------------------------------------------------------------------

(deftest custom-result-data-is-returned-on-failure
  (is (= {:foo :bar :baz [42]}
         (:result-data
          (tc/quick-check 100
                          (prop/for-all [x gen/nat]
                            (reify results/Result
                              (pass? [_] false)
                              (result-data [_]
                                {:foo :bar :baz [42]}))))))))

;; TCHECK-131
(deftest exception-results-are-treated-as-failures-for-backwards-compatibility
  (doseq [e [(#?(:clj Exception.
                 :cljs js/Error.)
              "Let's pretend this was thrown.")
             #?(:clj (Error. "Not an Exception, technically"))]]
    (is (false? (:pass?
                 (tc/quick-check 100
                                 (prop/for-all [x gen/nat]
                                   e)))))))
;; TCHECK-134
#?(:cljs
   (defn multiply-check [x y]
     (let [goog-math-long (.multiply x y)
           test-check (rl/* x y)]
       (and (== (.-high_ goog-math-long) (.-high_ test-check))
            (== (.-low_ goog-math-long) (.-low_ test-check))))))
#?(:cljs
    (deftest multiply-test-check-and-goog
      (testing "For goog.math.Long's test.check multiply is the same as goog.math.Long.multiply"
        (is (:result
              (tc/quick-check 1000 (prop/for-all* [gen/gen-raw-long gen/gen-raw-long] multiply-check)))))))
;; Count and concat work as expected
;; ---------------------------------------------------------------------------

(defn concat-counts-correct
  [a b]
  (= (count (concat a b))
     (+ (count a) (count b))))

(deftest count-and-concat
  (testing "For all vectors A and B:
           length(A + B) == length(A) + length(B)"
    (is (:result
         (let [p (prop/for-all* [(gen/vector gen/int)
                                 (gen/vector gen/int)] concat-counts-correct)]
           (tc/quick-check 1000 p))))))

;; Interpose (Count)
;; ---------------------------------------------------------------------------

(defn interpose-twice-the-length ;; (or one less)
  [v]
  (let [interpose-count (count (interpose :i v))
        original-count (count v)]
    (or
     (= (* 2 original-count) interpose-count)
     (= (dec (* 2 original-count)) interpose-count))))

(deftest interpose-creates-sequence-twice-the-length
  (testing "Interposing a collection with a value makes its count
           twice the original collection, or ones less."
    (is (:result
         (tc/quick-check 1000 (prop/for-all [v (gen/vector gen/int)] (interpose-twice-the-length v)))))))

;; Lists and vectors are equivalent with seq abstraction
;; ---------------------------------------------------------------------------

(defn list-vector-round-trip-equiv
  [a]
  ;; NOTE: can't use `(into '() ...)` here because that
  ;; puts the list in reverse order. clojure.test.check found that bug
  ;; pretty quickly...
  (= a (apply list (vec a))))

(deftest list-and-vector-round-trip
  (is (:result
       (tc/quick-check
        1000 (prop/for-all*
              [(gen/list gen/int)] list-vector-round-trip-equiv)))))

;; keyword->string->keyword roundtrip
;; ---------------------------------------------------------------------------

;; NOTE cljs: this is one of the slowest due to how keywords are constructed
;; drop N to 100 - David
(deftest keyword-symbol-serialization-roundtrip
  (testing "For all keywords and symbol, (comp read-string pr-str) is identity."
    (is (:result
         (tc/quick-check #?(:clj 1000 :cljs 100)
                         (prop/for-all [x (gen/one-of [gen/keyword
                                                       gen/keyword-ns
                                                       gen/symbol
                                                       gen/symbol-ns])]
                           (= x (read-string (pr-str x)))))))))

;; Boolean and/or
;; ---------------------------------------------------------------------------

(deftest boolean-or
  (testing "`or` with true and anything else should be true"
    (is (:result (tc/quick-check
                  1000 (prop/for-all*
                        [gen/boolean] #(or % true)))))))

(deftest boolean-and
  (testing "`and` with false and anything else should be false"
    (is (:result (tc/quick-check
                  1000 (prop/for-all*
                        [gen/boolean] #(not (and % false))))))))

;; Sorting
;; ---------------------------------------------------------------------------

(defn elements-are-in-order-after-sorting
  [v]
  (every? identity (map <= (partition 2 1 (sort v)))))

(deftest sorting
  (testing "For all vectors V, sorted(V) should have the elements in order"
    (is (:result
         (tc/quick-check
          1000
          (prop/for-all*
           [(gen/vector gen/int)] elements-are-in-order-after-sorting))))))

;; Constant generators
;; ---------------------------------------------------------------------------

;; A constant generator always returns its created value
(defspec constant-generators 100
  (prop/for-all [a (gen/return 42)]
    (= a 42)))

(deftest constant-generators-dont-shrink
  (testing "Generators created with `gen/return` should not shrink"
    (is (= [42]
           (let [result (tc/quick-check 100
                                        (prop/for-all
                                         [a (gen/return 42)]
                                          false))]
             (-> result :shrunk :smallest))))))

;; Tests are deterministic
;; ---------------------------------------------------------------------------

(defn vector-elements-are-unique
  [v]
  (== (count v) (count (distinct v))))

(defn dissoc-timing-keys
  [m]
  (-> m
      (dissoc :failed-after-ms)
      (update :shrunk dissoc :time-shrinking-ms)))

(defn unique-test
  [seed]
  (tc/quick-check 1000
                  (prop/for-all*
                   [(gen/vector gen/int)] vector-elements-are-unique)
                  :seed seed))

(defn equiv-runs
  [seed]
  (= (dissoc-timing-keys (unique-test seed))
     (dissoc-timing-keys (unique-test seed))))

(deftest tests-are-deterministic
  (testing "If two runs are started with the same seed, they should
           return the same results."
    (is (:result
         (tc/quick-check 1000 (prop/for-all* [gen/int] equiv-runs))))))

;; Generating basic generators
;; --------------------------------------------------------------------------
(deftest generators-test
  (let [t (fn [generator pred]
            (is (:result (tc/quick-check 100
                                         (prop/for-all [x generator]
                                           (pred x))))))
        is-char-fn #?(:clj char? :cljs string?)]

    (testing "keyword"              (t gen/keyword keyword?))

    ;; No ratio in cljs
    #?@(:clj [(testing "ratio"                (t gen/ratio   (some-fn ratio? integer?)))
              (testing "byte"                 (t gen/byte    #(instance? Byte %)))
              (testing "bytes"                (t gen/bytes   #(instance? (Class/forName "[B") %)))]) (testing "char"                 (t gen/char                 is-char-fn))
    (testing "char-ascii"           (t gen/char-ascii           is-char-fn))
    (testing "char-alphanumeric"    (t gen/char-alphanumeric    is-char-fn))
    (testing "string"               (t gen/string               string?))
    (testing "string-ascii"         (t gen/string-ascii         string?))
    (testing "string-alphanumeric"  (t gen/string-alphanumeric  string?))

    (testing "vector" (t (gen/vector gen/int) vector?))
    (testing "list"   (t (gen/list gen/int)   list?))
    (testing "map"    (t (gen/map gen/int gen/int) map?))))

;; such-that
;; --------------------------------------------------------------------------

(deftest such-that-allows-customizing-exceptions
  (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"Oh well!"
                        (gen/generate
                         (gen/such-that
                          #(apply distinct? %)
                          (gen/vector gen/boolean 5)
                          {:ex-fn (fn [{:keys [pred gen max-tries]}]
                                    (is (and pred gen max-tries))
                                    (#?(:clj Exception. :cljs js/Error.) "Oh well!"))})))))

;; Distinct collections
;; --------------------------------------------------------------------------

(def gen-distinct-generator
  (gen/elements [gen/list-distinct gen/vector-distinct
                 gen/set gen/sorted-set]))

(def gen-size-bounds-and-pred
  "Generates [pred size-opts], where size-opts is a map to pass to
  distinct generators, and pred is a predicate on the size of a
  collection, to check that it matches the options."
  (gen/one-of
   [(gen/return [(constantly true) {}])
    (gen/fmap (fn [num-elements]
                [#{num-elements} {:num-elements num-elements}])
              gen/nat)
    (gen/fmap (fn [min-elements]
                [#(<= min-elements %) {:min-elements min-elements}])
              gen/nat)
    (gen/fmap (fn [max-elements]
                [#(<= % max-elements) {:max-elements max-elements}])
              gen/nat)
    (gen/fmap (fn [bounds]
                (let [[min-elements max-elements] (sort bounds)]
                  [#(<= min-elements % max-elements)
                   {:min-elements min-elements
                    :max-elements max-elements}]))
              (gen/tuple gen/nat gen/nat))]))

(defspec map-honors-size-opts
  (prop/for-all [[the-map size-pred _]
                 (gen/bind gen-size-bounds-and-pred
                           (fn [[pred size-opts]]
                             (gen/fmap #(vector % pred size-opts)
                                       (gen/map gen/string gen/nat size-opts))))]
    (size-pred (count the-map))))

(defspec distinct-collections-honor-size-opts
  (prop/for-all [[the-coll size-pred _]
                 (gen/bind (gen/tuple gen-size-bounds-and-pred
                                      gen-distinct-generator)
                           (fn [[[pred size-opts] coll-gen]]
                             (gen/fmap #(vector % pred size-opts)
                                       (coll-gen gen/string size-opts))))]
    (size-pred (count the-coll))))

(defspec distinct-collections-are-distinct
  (prop/for-all [the-coll
                 (gen/bind (gen/tuple gen-size-bounds-and-pred
                                      gen-distinct-generator)
                           (fn [[[_ size-opts] coll-gen]]
                             (coll-gen gen/string size-opts)))]
    (or (empty? the-coll)
        (apply distinct? the-coll))))

(defspec distinct-by-collections-are-distinct-by 20
  (let [key-fn #(quot % 7)]
    (prop/for-all [the-coll
                   (gen/bind (gen/tuple gen-size-bounds-and-pred
                                        (gen/elements
                                         [(partial gen/vector-distinct-by key-fn)
                                          (partial gen/list-distinct-by key-fn)])
                                        gen-distinct-generator)
                             (fn [[[_ size-opts] coll-gen]]
                               (coll-gen (gen/choose -10000 10000) size-opts)))]
      (or (empty? the-coll)
          (apply distinct? (map key-fn the-coll))))))

(deftest distinct-generators-throw-when-necessary
  ;; I tried using `are` here but it breaks in cljs
  (doseq [g [gen/vector-distinct
             gen/list-distinct
             gen/set
             gen/sorted-set
             (partial gen/vector-distinct-by pr-str)
             (partial gen/list-distinct-by pr-str)]]
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"Couldn't generate enough distinct elements"
                          (first (gen/sample
                                  (g gen/boolean {:min-elements 5})))))
    (is (thrown-with-msg? #?(:clj Exception :cljs js/Error) #"foo bar"
                          (first (gen/sample
                                  (g gen/boolean {:min-elements 5
                                                  :ex-fn (fn [arg] (ex-info "foo bar" arg))}))))))
  (is (thrown? #?(:clj Exception :cljs js/Error)
               (first (gen/sample
                       (gen/map gen/boolean gen/nat {:min-elements 5}))))))

(defspec shrinking-respects-distinctness-and-sizing 20
  (prop/for-all [g gen-distinct-generator
                 seed gen-seed
                 size (gen/choose 1 20)
                 [pred opts] gen-size-bounds-and-pred]
    (let [rose-tree (gen/call-gen (g (gen/choose 0 1000) opts)
                                  (random/make-random seed) size)
          ;; inevitably some of these will be way too long to actually
          ;; test, so this is the easiest thing to do :/
          vals (take 1000 (rose/seq rose-tree))]
      (every? (fn [coll]
                (and (or (empty? coll)
                         (apply distinct? coll))
                     (pred (count coll))))
              vals))))

(defspec distinct-generators-can-shrink-in-size 20
  (prop/for-all [g gen-distinct-generator
                 seed gen-seed
                 size (gen/choose 1 20)]
    (let [rose-tree (gen/call-gen (g (gen/choose 1 1000))
                                  (random/make-random seed) size)
          a-shrink (->> rose-tree
                        (iterate #(first (rose/children %)))
                        (take-while identity)
                        (map rose/root))]
      (and (apply > (map #(reduce + %) a-shrink))
           (empty? (last a-shrink))))))

(defspec distinct-collections-are-not-biased-in-their-ordering 5
  (prop/for-all [g (gen/elements [gen/vector-distinct gen/list-distinct])
                 seed gen-seed]
    (let [rng (random/make-random seed)]
      (every?
       (->> (gen/lazy-random-states rng)
            (take 1000)
            (map #(rose/root (gen/call-gen (g gen/nat {:num-elements 3, :max-tries 100}) % 0)))
            (set))
       [[0 1 2] [0 2 1] [1 0 2] [1 2 0] [2 0 1] [2 1 0]]))))

(defspec distinct-collections-with-few-possible-values 20
  (prop/for-all [boolean-sets (gen/vector (gen/resize 5 (gen/set gen/boolean)) 1000)]
    (= 4 (count (distinct boolean-sets)))))

(deftest can't-generate-set-of-five-booleans
  (let [ex (try
             (gen/generate (gen/set gen/boolean {:num-elements 5}))
             (is false)
             (catch #?(:clj Exception :cljs js/Error) e
               e))]
    (is (re-find #"Couldn't generate enough distinct elements"
                 #? (:clj (.getMessage ^Exception ex) :cljs (.-message ex))))
    (is (= 5 (-> ex ex-data :num-elements)))))

;; Generating proper matrices
;; ---------------------------------------------------------------------------

(defn proper-matrix?
  "Check if provided nested vectors form a proper matrix — that is, all nested
   vectors have the same length"
  [mtx]
  (let [first-size (count (first mtx))]
    (every? (partial = first-size) (map count (rest mtx)))))

(deftest proper-matrix-test
  (testing "can generate proper matrices"
    (is (:result (tc/quick-check
                  100 (prop/for-all
                       [mtx (gen/vector (gen/vector gen/int 3) 3)]
                        (proper-matrix? mtx)))))))

(def bounds-and-vector
  (gen/bind (gen/tuple gen/s-pos-int gen/s-pos-int)
            (fn [[a b]]
              (let [minimum (min a b)
                    maximum (max a b)]
                (gen/tuple (gen/return [minimum maximum])
                           (gen/vector gen/int minimum maximum))))))

(deftest proper-vector-test
  (testing "can generate vectors with sizes in a provided range"
    (is (:result (tc/quick-check
                  100 (prop/for-all
                       [b-and-v bounds-and-vector]
                        (let [[[minimum maximum] v] b-and-v
                              c (count v)]
                          (and (<= c maximum)
                               (>= c minimum)))))))))

;; Tuples and Pairs retain their count during shrinking
;; ---------------------------------------------------------------------------

(defn n-int-generators
  [n]
  (vec (repeat n gen/int)))

(def tuples
  [(apply gen/tuple (n-int-generators 1))
   (apply gen/tuple (n-int-generators 2))
   (apply gen/tuple (n-int-generators 3))
   (apply gen/tuple (n-int-generators 4))
   (apply gen/tuple (n-int-generators 5))
   (apply gen/tuple (n-int-generators 6))])

(defn get-tuple-gen
  [index]
  (nth tuples (dec index)))

(defn inner-tuple-property
  [size]
  (prop/for-all [t (get-tuple-gen size)]
    false))

(defspec tuples-retain-size-during-shrinking 1000
  (prop/for-all [index (gen/choose 1 6)]
    (let [result (tc/quick-check
                  100 (inner-tuple-property index))]
      (= index (count (-> result
                          :shrunk :smallest first))))))

;; Bind works
;; ---------------------------------------------------------------------------

(def nat-vec
  (gen/such-that not-empty
                 (gen/vector gen/nat)))

(def vec-and-elem
  (gen/bind nat-vec
            (fn [v]
              (gen/tuple (gen/elements v) (gen/return v)))))

(defspec element-is-in-vec 100
  (prop/for-all [[element coll] vec-and-elem]
    (some #{element} coll)))

;; fmap is respected during shrinking
;; ---------------------------------------------------------------------------

(def plus-fifty
  (gen/fmap (partial + 50) gen/nat))

(deftest f-map-respected-during-shrinking
  (testing "Generators created with fmap should have that function applied
           during shrinking"
    (is (= [50]
           (let [result (tc/quick-check 100
                                        (prop/for-all
                                         [a plus-fifty]
                                          false))]
             (-> result :shrunk :smallest))))))

;; Collections can shrink reasonably fast; regression for TCHECK-94
;; ---------------------------------------------------------------------------

(defspec collections-shrink-quickly 200
  (prop/for-all [seed gen-seed
                 coll-gen (let [coll-generators
                                [gen/vector gen/list #_#_#_#_gen/set
                                 gen/vector-distinct gen/list-distinct
                                 #(gen/map % %)]
                                coll-gen-gen (gen/elements coll-generators)]
                            (gen/one-of [coll-gen-gen
                                         #_
                                         (gen/fmap (fn [[g1 g2]] (comp g1 g2))
                                                   (gen/tuple coll-gen-gen
                                                              coll-gen-gen))]))]
    (let [g (coll-gen (gen/frequency [[100 gen/large-integer]
                                      [1 (gen/return :oh-no!)]]))
          scalars #(remove coll? (tree-seq coll? seq %))
          ;; technically this could fail to fail, but running the test
          ;; 10000 times should ensure it doesn't
          result (tc/quick-check 10000
                                 (prop/for-all [coll g]
                                   (->> coll
                                        (scalars)
                                        (not-any? #{:oh-no!})))
                                 :seed seed)

          failing-size (-> result :fail first scalars count)
          {:keys [smallest total-nodes-visited]} (:shrunk result)]
      (and (< (count (scalars (first smallest))) 4)
           ;; shrink-time should be in proportion to the log of the
           ;; collection size; multiplying by 3 to add some wiggle
           ;; room
           (< total-nodes-visited (+ 5 (* 3 (Math/log failing-size))))))))

;; gen/int returns an integer when size is a double; regression for TCHECK-73
;; ---------------------------------------------------------------------------

(def gen-double
  (gen/fmap (fn [[x y]] (double (+ x (/ y 10))))
            (gen/tuple gen/pos-int (gen/choose 0 9))))

(defspec gen-int-with-double-size 1000
  (prop/for-all [size gen-double]
    (integer? (gen/generate gen/int size))))

;; recursive-gen doesn't change ints to doubles; regression for TCHECK-73
;; ---------------------------------------------------------------------------

(defspec recursive-generator-test 100
  (let [btree* (fn [g] (gen/hash-map
                        :value gen/int
                        :left g
                        :right g))
        btree (gen/recursive-gen btree* (gen/return nil))
        valid? (fn valid? [tree]
                 (and (integer? (:value tree))
                      (or (nil? (:left tree))
                          (valid? (:left tree)))
                      (or (nil? (:right tree))
                          (valid? (:right tree)))))]
    (prop/for-all [t btree] (or (nil? t)
                                (valid? t)))))

;; NOTE cljs: adjust for JS numerics - NB

#?(:clj
   (deftest calc-long-increasing
  ;; access internal gen/calc-long function for testing
     (are [low high] (apply < (map #(@#'gen/calc-long % low high) (range 0.0 0.9999 0.111)))
      ;; low and high should not be too close, 100 is a reasonable spread
       (- Long/MAX_VALUE 100) Long/MAX_VALUE
       Long/MIN_VALUE (+ Long/MIN_VALUE 100)
       Long/MIN_VALUE 0
       0 100
       -100 0
       0 Long/MAX_VALUE
       Long/MIN_VALUE Long/MAX_VALUE)))

;; edn rountrips
;; ---------------------------------------------------------------------------

(def simple-type
  "Like gen/simple-type but excludes Infinity and NaN."
  (gen/one-of [gen/int gen/large-integer (gen/double* {:infinite? false, :NaN? false}) gen/char gen/string
               gen/ratio gen/boolean gen/keyword gen/keyword-ns gen/symbol gen/symbol-ns gen/uuid]))

(def any-edn (gen/recursive-gen gen/container-type simple-type))

(defn edn-roundtrip?
  [value]
  (= value (-> value prn-str edn/read-string)))

(defspec edn-roundtrips 200
  (prop/for-all [a any-edn]
    (edn-roundtrip? a)))

;; not-empty works
;; ---------------------------------------------------------------------------

(defspec not-empty-works 100
  (prop/for-all [v (gen/not-empty (gen/vector gen/boolean))]
    (not-empty v)))

;; no-shrink works
;; ---------------------------------------------------------------------------

(defn run-no-shrink
  [i]
  (tc/quick-check 100
                  (prop/for-all [coll (gen/vector gen/nat)]
                    (some #{i} coll))))

(defspec no-shrink-works 100
  (prop/for-all [i gen/nat]
    (let [result (run-no-shrink i)]
      (if (:result result)
        true
        (= (:fail result)
           (-> result :shrunk :smallest))))))

;; elements works with a variety of input
;; ---------------------------------------------------------------------------

(deftest elements-with-empty
  (is (thrown? #?(:clj AssertionError :cljs js/Error)
               (gen/elements ()))))

(defspec elements-with-a-set 100
  (prop/for-all [num (gen/elements #{9 10 11 12})]
    (<= 9 num 12)))

;; choose respects bounds during shrinking
;; ---------------------------------------------------------------------------

(def range-gen
  (gen/fmap (fn [[a b]]
              [(min a b) (max a b)])
            (gen/tuple gen/int gen/int)))

(defspec choose-respects-bounds-during-shrinking 100
  (prop/for-all [[mini maxi] range-gen
                 random-seed gen/nat
                 size gen/nat]
    (let [tree (gen/call-gen
                (gen/choose mini maxi)
                (random/make-random random-seed)
                size)]
      (every?
       #(and (<= mini %) (>= maxi %))
       (rose/seq tree)))))

;; rand-range copes with full range of longs as bounds
;; ---------------------------------------------------------------------------

;; NOTE cljs: need to adjust for JS numerics - David

#?(:clj
   (deftest rand-range-copes-with-full-range-of-longs
     (let [[low high] (reduce
                       (fn [[low high :as margins] x]
                         (cond
                           (< x low) [x high]
                           (> x high) [low x]
                           :else margins))
                       [Long/MAX_VALUE Long/MIN_VALUE]
                    ; choose uses rand-range directly, reasonable proxy for its
                    ; guarantees
                       (take 1e6 (gen/sample-seq (gen/choose Long/MIN_VALUE Long/MAX_VALUE))))]
       (is (< low high))
       (is (< low Integer/MIN_VALUE))
       (is (> high Integer/MAX_VALUE)))))

;; rand-range yields values inclusive of both lower & upper bounds provided to it
;; further, that generators that use rand-range use its full range of values
;; ---------------------------------------------------------------------------

(deftest rand-range-uses-inclusive-bounds
  (let [bounds [5 7]
        rand-range (fn [r] (apply #'gen/rand-range r bounds))]
    (loop [trials 0
           bounds (set bounds)
           r (random/make-random)]
      (cond
        (== trials 10000)
       ;; the probability of this happening by chance is roughly 1 in
       ;; 10^1761 so we can safely assume something's wrong if it does
        (is nil "rand-range didn't return both of its bounds after 10000 trials")

        (empty? bounds) (is true)
        :else (let [[r1 r2] (random/split r)]
                (recur (inc trials) (disj bounds (rand-range r1)) r2))))))

(deftest elements-generates-all-provided-values
  (let [options [:a 42 'c/d "foo"]]
    (is (->> (reductions
              disj
              (set options)
              (gen/sample-seq (gen/elements options)))
             (take 10000)
             (some empty?))
        ;; the probability of this happening by chance is roughly 1 in
        ;; 10^1249 so we can safely assume something's wrong if it does
        "elements didn't return all of its candidate values after 10000 trials")))

;; shuffling a vector generates a permutation of that vector
;; ---------------------------------------------------------------------------

(def original-vector-and-permutation
  (gen/bind (gen/vector gen/int)
            #(gen/tuple (gen/return %) (gen/shuffle %))))

(defspec shuffled-vector-is-a-permutation-of-original 100
  (prop/for-all [[coll permutation] original-vector-and-permutation]
    (= (sort coll) (sort permutation))))

;; UUIDs
;; ---------------------------------------------------------------------------

(defspec uuid-generates-uuids
  (prop/for-all [uuid gen/uuid]
    (and (instance? #?(:clj java.util.UUID :cljs cljs.core.UUID) uuid)
         ;; check that we got the special fields right
         (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"
                     (str uuid)))))

(deftest uuid-generates-distinct-values
  (is (apply distinct?
             (gen/sample gen/uuid 1000))))

;; large integers
;; ---------------------------------------------------------------------------

(defn gen-bounds-and-pred
  "Generates a map that may contain :min and :max, and a pred
  that checks a number satisfies those constraints."
  [num-gen]
  (gen/one-of [(gen/return [{} (constantly true)])
               (gen/fmap (fn [x] [{:min x} #(<= x %)]) num-gen)
               (gen/fmap (fn [x] [{:max x} #(<= % x)]) num-gen)
               (gen/fmap (fn [bounds]
                           (let [[lb ub] (sort bounds)]
                             [{:min lb :max ub} #(<= lb % ub)]))
                         (gen/tuple num-gen num-gen))]))

(def MAX_INTEGER #?(:clj Long/MAX_VALUE :cljs (dec (apply * (repeat 53 2)))))
(def MIN_INTEGER #?(:clj Long/MIN_VALUE :cljs (- MAX_INTEGER)))

(defn native-integer?
  [x]
  #?(:clj (instance? Long x)
     :cljs (and (integer? x) (<= MIN_INTEGER x MAX_INTEGER))))

(defspec large-integer-spec 500
  (prop/for-all [x gen/large-integer]
    (native-integer? x)))

(defspec large-integer-bounds-spec 500
  (prop/for-all [[opts pred x]
                 (gen/bind (gen-bounds-and-pred gen/large-integer)
                           (fn [[opts pred]]
                             (gen/fmap #(vector opts pred %)
                                       (gen/large-integer* opts))))]
    (pred x)))

(defspec large-integer-distribution-spec 5
  (prop/for-all [xs (gen/no-shrink
                     (gen/vector (gen/resize 150 gen/large-integer) 10000))]
    (every? (fn [[lb ub]]
              (some #(<= lb % ub) xs))
            [[0 10]
             [-10 -1]
             [10000 100000]
             [-100000 -10000]
             [MIN_INTEGER (/ MIN_INTEGER 2)]
             [(/ MAX_INTEGER 2) MAX_INTEGER]])))

;; doubles
;; ---------------------------------------------------------------------------

(defn infinite?
  [x]
  #?(:clj (Double/isInfinite x)
     :cljs (or (= @#'gen/POS_INFINITY x)
               (= @#'gen/NEG_INFINITY x))))

(defn nan?
  [x]
  #?(:clj (Double/isNaN x)
     :cljs (.isNaN js/Number x)))

(defspec double-test 100
  (prop/for-all [x gen/double]
    #?(:clj (instance? Double x)
       :cljs (number? x))))

(defspec double-distribution-test 5
  (prop/for-all [xs (gen/no-shrink
                     (gen/vector (gen/resize 100 gen/double) 10000))]
    (and (some #(= @#'gen/POS_INFINITY %) xs)
         (some #(= @#'gen/NEG_INFINITY %) xs)
         (some nan? xs)
         (every? (fn [[lb ub]]
                   (some #(<= lb % ub) xs))
                 [[-1e303 -1e200]
                  [-1e200 -1e100]
                  [-1e100 -1.0]
                  [0.0 0.0]
                  [1.0 1e100]
                  [1e100 1e200]
                  [1e200 1e303]])
         (let [mods (->> xs
                         (remove infinite?)
                         (remove nan?)
                         (map #(mod % 1.0)))]
           (every? (fn [[lb ub]]
                     (some #(<= lb % ub) mods))
                   [[0.0 0.1]
                    [0.1 0.2]
                    [0.25 0.75]
                    [0.8 0.9]
                    [0.9 1.0]])))))

(defspec double-bounds-spec 500
  (prop/for-all [[opts pred x]
                 (gen/bind (gen-bounds-and-pred (gen/double* {:infinite? false, :NaN? false}))
                           (fn [[opts pred]]
                             (gen/fmap #(vector opts pred %)
                                       (gen/double* (assoc opts
                                                           :infinite? false,
                                                           :NaN? false)))))]
    (pred x)))

;; vector can generate large vectors; regression for TCHECK-49
;; ---------------------------------------------------------------------------

(deftest large-vector-test
  (is (= 100000
         (count (first (gen/sample
                        (gen/vector gen/nat 100000)
                        1))))))

;; scale controls growth rate of generators
;; ---------------------------------------------------------------------------

(deftest scale-test
  (let [g (gen/scale (partial min 10) gen/pos-int) ;; should limit size to 10
        samples (gen/sample g 1000)]
    (is (every? (partial >= 11) samples))
    (is (some (partial = 10) samples))))

;; generator dev helpers
;; ---------------------------------------------------------------------------

(deftest generate-test
  (is (string? (gen/generate gen/string)))
  (is (string? (gen/generate gen/string 42))))

(defspec generate-with-seed-test
  (prop/for-all [seed gen-seed
                 size gen/nat]
    (apply = (repeatedly 5 #(gen/generate gen/string size seed)))))

;; defspec macro
;; ---------------------------------------------------------------------------

(defspec run-only-once 1 (prop/for-all* [gen/int] (constantly true)))

(defspec run-default-times (prop/for-all* [gen/int] (constantly true)))

(defspec run-with-map1 {:num-tests 1} (prop/for-all* [gen/int] (constantly true)))

;; run-with-map succeeds only because gen/int returns 0 for its first result.  If it runs
;; multiple trials, we expect a failure.  This test verifies that the num-tests works.
(defspec run-with-map {:num-tests 1
                       :seed 1}
  (prop/for-all [a gen/int]
    (= a 0)))

(def my-defspec-options {:num-tests 1 :seed 1})

;; Regression test for old anaphoric issue with defspec: "seed", "times", and "max-size" were
;; used literally as parameters in the test function signature and could unexpectedly
;; shadow lexical names.
(def seed 0)

(defspec run-with-symbolic-options my-defspec-options
  (prop/for-all [a gen/int]
    (= a seed)))

(defspec run-with-no-options
  (prop/for-all [a gen/int]
    (integer? a)))

(defspec run-float-time 1e3
  (prop/for-all [a gen/int]
    (integer? a)))

;; verify that the created tests work when called by name with options
(deftest spec-called-with-options
  (is (= (select-keys (run-only-once) [:num-tests :result]) {:num-tests 1 :result true}))
  ;; run-with-map should succeed only if it runs exactly once, otherwise it's expected to fail
  (is (= (select-keys (run-with-map) [:num-tests :result]) {:num-tests 1 :result true}))
  (is (false? (:result (run-with-map 25))))
  (is (false? (:result (run-with-symbolic-options 100 :seed 1 :max-size 20))))
  (is (:result (run-with-symbolic-options 1 :seed 1 :max-size 20))))

;; let macro
;; ---------------------------------------------------------------------------

(defspec let-as-fmap-spec 20
  (prop/for-all [s (gen/let [n gen/nat]
                     (str n))]
    (re-matches #"\d+" s)))

(defspec let-as-bind-spec 20
  (prop/for-all [[xs x] (gen/let [xs (gen/not-empty (gen/vector gen/nat))]
                          (gen/tuple (gen/return xs) (gen/elements xs)))]
    (some #{x} xs)))

(defspec let-with-multiple-clauses-spec 20
  (prop/for-all [[xs x] (gen/let [xs (gen/not-empty (gen/vector gen/nat))
                                  x (gen/elements xs)]
                          [xs x])]
    (some #{x} xs)))

;; A test to maintain the behavior assumed by TCHECK-133
(defspec independent-let-clauses-shrink-correctly 10
  (let [gen-let-with-independent-clauses
        ;; gen/let unnecessarily ties these three generators together
        ;; with gen/bind rather than gen/tuple (because it can't
        ;; reliably detect independence), which theoretically could
        ;; lead to poor shrinking, but because the immutable random
        ;; number generator gets reused during gen/bind shrinking, it
        ;; turns out okay (the y and z generators get re-run, but with
        ;; the same parameters, so they generate the same value)
        (gen/let [x gen/large-integer
                  y gen/large-integer
                  z gen/large-integer]
          [x y z])

        failing-prop
        (prop/for-all [nums gen-let-with-independent-clauses]
          (not (every? #(< 100 % 1000) nums)))]
    (prop/for-all [seed gen-seed]
      ;; I suspect that this property is likely enough to fail
      ;; that 1000000 trials will virtually always trigger it,
      ;; but I haven't done the math on that.
      (let [res (tc/quick-check 1000000 failing-prop :seed seed)]
        (and (false? (:result res))
             (= [101 101 101]
                (-> res :shrunk :smallest first)))))))

;; reporter-fn
;; ---------------------------------------------------------------------------

(deftest reporter-fn-calls-test
  (testing "a failing prop"
    (let [calls (atom [])
          reporter-fn (fn [arg]
                        #?(:clj (is (specs/valid-reporter-fn-call? arg)))
                        (swap! calls conj arg))
          prop (prop/for-all [n gen/nat]
                 (> 5 n))]
      (tc/quick-check 1000 prop :reporter-fn reporter-fn)
      (is (= #{:trial :failure :shrink-step :shrunk}
             (->> @calls (map :type) set)))))

  (testing "a successful prop"
    (let [calls (atom [])
          reporter-fn (fn [arg]
                        #?(:clj (is (specs/valid-reporter-fn-call? arg)))
                        (swap! calls conj arg))
          prop (prop/for-all [n gen/nat]
                 (<= 0 n))]
      (tc/quick-check 5 prop :reporter-fn reporter-fn)
      (is (= #{:trial :complete}
             (->> @calls (map :type) set))))))

(deftest shrink-step-events-test
  (let [events (atom [])
        reporter-fn (partial swap! events conj)
        pred (fn [n] (not (< 100 n)))
        prop (prop/for-all [n (gen/scale (partial * 10) gen/nat)]
               (pred n))]
    (tc/quick-check 100 prop :reporter-fn reporter-fn)
    (let [shrink-steps (filter #(= :shrink-step (:type %)) @events)
          [passing-steps failing-steps] ((juxt filter remove)
                                         #(-> % :shrinking :result results/pass?)
                                         shrink-steps)
          get-args-and-smallest-args (juxt (comp first :args :shrinking)
                                           (comp first :smallest :shrinking))]
      (is (every? #(not (pred (-> % :shrinking :args first))) failing-steps)
          "pred on args is falsey in all failing steps")
      (is (every? #(pred (-> % :shrinking :args first)) passing-steps)
          "pred on args is truthy in all passing steps")
      (is (->> failing-steps
               (map get-args-and-smallest-args)
               (every? (fn [[args current-smallest-args]] (= args current-smallest-args))))
          "for every failing step, current-smallest args are equal to args")
      (is (->> passing-steps
               (map get-args-and-smallest-args)
               (every? (fn [[args current-smallest-args]] (< args current-smallest-args))))
          "for every passing step, current-smallest args are smaller than args")
      (let [shrunk-args (map (comp first :args) failing-steps)]
        (is (= shrunk-args
               (reverse (sort shrunk-args)))
            "failing steps args are sorted in descending order")))))

;; TCHECK-77 Regression
;; ---------------------------------------------------------------------------

;; Note cljs: need to adjust for JS numerics - NB
#?(:clj
   (deftest choose-distribution-sanity-check
     (testing "Should not get the same random value more than 90% of the time"
    ;; This is a probabilistic test; the odds of a false-positive
    ;; failure for the ranges with two elements should be roughly 1 in
    ;; 10^162 (and even rarer for larger ranges), so it will never
    ;; ever happen.
       (are [low high] (let [xs (gen/sample (gen/choose low high) 1000)
                             count-of-most-frequent (apply max (vals (frequencies xs)))]
                         (< count-of-most-frequent 900))
         (dec Long/MAX_VALUE) Long/MAX_VALUE
         Long/MIN_VALUE (inc Long/MIN_VALUE)
         Long/MIN_VALUE 0
         0 1
         -1 0
         0 Long/MAX_VALUE
         Long/MIN_VALUE Long/MAX_VALUE))))

;; TCHECK-82 Regression
;; ---------------------------------------------------------------------------

(deftest shrinking-laziness-test
  (testing "That the shrinking process doesn't accidentally do extra work"
    (let [state (atom 80)
          prop (prop/for-all [xs (gen/vector gen/large-integer)]
                 (pos? (swap! state dec)))
          res (tc/quick-check 100 prop :seed 42)
          test-runs-during-shrinking (- @state)]
      (is (= test-runs-during-shrinking
             (get-in res [:shrunk :total-nodes-visited]))))))

;; TCHECK-32 Regression
;; ---------------------------------------------------------------------------

;; The original code reported in TCHECK-32 used 100 test runs,
;; but I'm using 50 here because 100 pushes the limits of my
;; node.js default memory allocation. I don't consider that
;; the same as the original problem, though, because it's just
;; as likely to OOM for (let [g (gen/vector gen/string)] (gen/map g g)),
;; which shows that the problem has more to do with 2D collections of
;; strings/etc. cranked up to size=200 than it has to do with
;; gen/any in particular.
(defspec merge-is-idempotent-and-this-spec-doesn't-OOM 50
  ;; using any-edn instead of gen/any here because:
  ;;
  ;; - NaN is problematic as a map key in general
  ;; - NaN/infinity are a problem as a map key and set element on CLJS
  ;;   because of CLJS-1594
  ;; - NaN can be equal to itself in clj when identical? checks
  ;;   short-circuit equality, but this doesn't seem to happen in CLJS
  (prop/for-all [m (gen/map any-edn any-edn)]
    (= m (merge m m))))

(defn frequency-shrinking-prop
  [bad-weight]
  (prop/for-all [x (gen/frequency [[bad-weight (gen/return [:gen1 :bad])]
                                   [10 (gen/fmap (partial vector :gen2)
                                                 (gen/list (gen/elements [:good
                                                                          42
                                                                          "a string"
                                                                          :bad])))]])]
    (not (re-find #"bad" (pr-str x)))))

;; TCHECK-114 Regression
;; ---------------------------------------------------------------------------

(deftest frequency-should-be-able-to-shrink-to-earlier-generators
  (let [prop (frequency-shrinking-prop 1)]
    ;; we can't test that gen2 ALWAYS shrinks to gen1 because of TCHECK-120
    (is (->> (range 1000)
             (map #(tc/quick-check 1000 prop :seed %))
             (some (fn [{:keys [fail shrunk]}]
                     (and shrunk
                          (= :gen2 (ffirst fail))
                          (= :gen1 (ffirst (:smallest shrunk))))))))))

;; TCHECK-129 Regression
;; ---------------------------------------------------------------------------

(deftest frequency-should-not-shrink-to-zero-weighted-generators
  (let [prop (frequency-shrinking-prop 0)]
    (is (->> (range 1000)
             (map #(tc/quick-check 1000 prop :seed %))
             (not-any? (fn [{:keys [fail shrunk]}]
                         (and shrunk
                              (= :gen2 (ffirst fail))
                              (= :gen1 (ffirst (:smallest shrunk))))))))))

;; prop/for-all
;; ---------------------------------------------------------------------------

(deftest for-all-takes-multiple-expressions
  (let [a (atom [])
        p (prop/for-all [x gen/nat]
            (swap! a conj x)
            (= x x))]
    (is (:result (tc/quick-check 1000 p)))
    (is (= 1000 (count @a)))
    (is (every? integer? @a))))

;; TCHECK-142
;; ---------------------------------------------------------------------------

(deftest quick-check-result-keys-test
  (testing "Pass"
    (let [m (tc/quick-check 10 (prop/for-all [x gen/nat] x))]
      (is (true? (:result m)))
      (is (true? (:pass? m)))
      (is (not (contains? m :result-data)))))
  (testing "Falsy Fail"
    (let [m (tc/quick-check 10 (prop/for-all [x gen/nat] false))]
      (is (false? (:result m)))
      (is (false? (:pass? m)))
      (is (nil? (:result-data m)))))
  (testing "Protocol Fail"
    (let [m (tc/quick-check 1000 (prop/for-all [x gen/nat]
                                   (reify results/Result
                                     (pass? [_] (< x 70))
                                     (result-data [_] {:foo 42 :x x}))))]
      (is (false? (:result m)))
      (is (false? (:pass? m)))
      (let [[x] (:fail m)]
        (is (= {:foo 42 :x x} (:result-data m))))
      (is (= {:foo 42 :x 70} (:result-data (:shrunk m))))))
  (testing "Error"
    (let [m (tc/quick-check 1000 (prop/for-all [x gen/nat]
                                   (or (< x 70)
                                       (throw (ex-info "Dang!" {:x x})))))]
      ;; okay maybe this is where cljs needs to do something different
      (is (instance? #?(:clj  clojure.lang.ExceptionInfo
                        :cljs ExceptionInfo)
                     (:result m))
          "legacy position for the error object")
      (is (false? (:pass? m)))
      (is (= [::prop/error] (keys (:result-data m))))
      (let [[x] (:fail m)]
        (is (= {:x x} (-> m :result-data ::prop/error ex-data))))
      (is (= 70 (-> m :shrunk :result-data ::prop/error ex-data :x))))))

;; TCHECK-150
;; ---------------------------------------------------------------------------

#?(:cljs
   (deftest throwing-arbitrary-objects-fails-tests-in-cljs
     (let [res (tc/quick-check 10 (prop/for-all [x gen/nat] (throw "a string")))]
       (is (false? (:pass? res)) "is definitely a failure")
       (is (:shrunk res) "evidenced by the fact that it shrunk")
       (is (instance? js/Error (:result res))
           "The legacy :result key has an Error object so nobody gets confused"))))

;; TCHECK-95
;; ---------------------------------------------------------------------------

(deftest timing-keys
  (let [{:keys [time-elapsed-ms]}
        (tc/quick-check 10 (prop/for-all [x gen/nat] (= x x x x)))]
    (is (integer? time-elapsed-ms))
    (is (<= 0 time-elapsed-ms)))
  (let [{:keys [failed-after-ms]
         {:keys [time-shrinking-ms]} :shrunk}
        (tc/quick-check 1000 (prop/for-all [x gen/nat] (not (<= 47 x 55))))]
    (is (integer? failed-after-ms))
    (is (<= 0 failed-after-ms))
    (is (integer? time-shrinking-ms))
    (is (<= 0 time-shrinking-ms))))
