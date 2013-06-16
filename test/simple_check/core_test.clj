(ns simple-check.core-test
  (:use clojure.test)
  (:require [simple-check.core       :as sc]
            [simple-check.generators :as gen]))

;; plus and 0 form a monoid
;; ---------------------------------------------------------------------------

(defn passes-monoid-properties
  [a b c]
  (and (= (+ 0 a) a)
       (= (+ a 0) a)
       (= (+ a (+ b c)) (+ (+ a b) c))))

(deftest plus-and-0-are-a-monoid
  (testing "+ and 0 form a monoid"
           (is (let [a gen/int]
                 (:result
                   (sc/quick-check 1000 passes-monoid-properties [a a a]))))))

;; reverse
;; ---------------------------------------------------------------------------

(defn reverse-equal?-helper
  [l]
  (let [r (vec (reverse l))]
    (and (= (count l) (count r))
         (= (seq l) (rseq r)))))

(deftest reverse-equal?
  (testing "For all vectors L, reverse(reverse(L)) == L"
           (is (let [v (gen/vector gen/int)]
                 (:result (sc/quick-check 1000 reverse-equal?-helper [v]))))))

;; failing reverse
;; ---------------------------------------------------------------------------

(deftest bad-reverse-test
  (testing "For all vectors L, L == reverse(L). Not true"
           (is (false?
                 (let [v (gen/vector gen/int)]
                   (:result (sc/quick-check 1000 #(= (reverse %) %) [v])))))))

;; failing element remove
;; ---------------------------------------------------------------------------

(defn first-is-gone
  [l]
  (not (some #{(first l)} (vec (rest l)))))

(deftest bad-remove
  (testing "For all vectors L, if we remove the first element E, E should not
           longer be in the list. (This is a false assumption)"
           (is (false?
                 (let [v (gen/vector gen/int)]
                   (:result (sc/quick-check 1000 first-is-gone [v])))))))

;; exceptions shrink and return as result
;; ---------------------------------------------------------------------------

(def exception (Exception. "I get caught"))

(defn exception-thrower
  [& args]
  (throw exception))

(deftest exceptions-are-caught
  (testing "Exceptions during testing are caught. They're also shrunk as long
           as they continue to throw."
           (is (= [exception [0]]
                  (let [result (sc/quick-check 1000 exception-thrower [gen/int])]
                    [(:result result) (get-in result [:shrunk :smallest])])))))

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
                 (let [v (gen/vector gen/int)]
                   (sc/quick-check 1000 concat-counts-correct [v v]))))))

;; Interpose (Count)
;; ---------------------------------------------------------------------------

(defn interpose-twice-the-length ;; (or one less)
  [v]
  (let [interpose-count (count (interpose :i v))]
    (or
      (= (* 2 interpose-count))
      (= (dec (* 2 interpose-count))))))


(deftest interpose-creates-sequence-twice-the-length
  (testing
    "Interposing a collection with a value makes it's count
    twice the original collection, or ones less."
    (is (:result
          (sc/quick-check 1000 interpose-twice-the-length
                          [(gen/vector gen/int)])))))

;; Lists and vectors are equivalent with seq abstraction
;; ---------------------------------------------------------------------------

(defn list-vector-round-trip-equiv
  [a]
  ;; NOTE: can't use `(into '() ...)` here because that
  ;; puts the list in reverse order. simple-check found that bug
  ;; pretty quickly...
  (= a (apply list (vec a))))

(deftest list-and-vector-round-trip
  (testing
    ""
    (is (:result
          (sc/quick-check 1000 list-vector-round-trip-equiv
                          [(gen/list gen/int)])))))

;; keyword->string->keyword roundtrip
;; ---------------------------------------------------------------------------

(def keyword->string->keyword (comp keyword clojure.string/join rest str))

(defn keyword-string-roundtrip-equiv
  [k]
  (= k (keyword->string->keyword k)))

(deftest keyword-string-roundtrip
  (testing
    "For all keywords, turning them into a string and back is equivalent
    to the original string (save for the `:` bit)"
    (is (:result
          (sc/quick-check 1000 keyword-string-roundtrip-equiv
                          [gen/keyword])))))

;; Sorting
;; ---------------------------------------------------------------------------

(defn elements-are-in-order-after-sorting
  [v]
  (every? identity (map <= (partition 2 1 (sort v)))))

(deftest sorting
  (testing
    "For all vectors V, sorted(V) should have the elements in order"
    (is (:result
          (sc/quick-check 1000 elements-are-in-order-after-sorting
                          [(gen/vector gen/int)])))))

;; Tests are deterministic
;; ---------------------------------------------------------------------------

(defn vector-elements-are-unique
  [v]
  (== (count v) (count (distinct v))))

(defn unique-test
  [seed]
  (sc/quick-check 1000 vector-elements-are-unique
                  [(gen/vector gen/int)] :seed seed))

(defn equiv-runs
  [seed]
  (= (unique-test seed) (unique-test seed)))

(deftest tests-are-deterministic
  (testing "If two runs are started with the same seed, they should
           return the same results."
           (is (:result
                 (sc/quick-check 1000 equiv-runs [gen/int])))))
