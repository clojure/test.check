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
                   (sc/quick-check 100 passes-monoid-properties [a a a]))))))

;; reverse
;; ---------------------------------------------------------------------------

(defn reverse-equal?-helper
  [l]
  (let [r (vec (reverse l))]
    (and (= (count l) (count r))
         (= (seq l) (rseq r)))))

(deftest reverse-equal?
  (testing "For all lists L, reverse(reverse(L)) == L"
           (is (let [v (gen/vector gen/int)]
                 (:result (sc/quick-check 100 reverse-equal?-helper [v]))))))

;; failing reverse
;; ---------------------------------------------------------------------------

(deftest bad-reverse-test
  (testing "For all lists L, L == reverse(L). Not true"
           (is (false?
                 (let [v (gen/vector gen/int)]
                   (:result (sc/quick-check 100 #(= (reverse %) %) [v])))))))

;; failing element remove
;; ---------------------------------------------------------------------------

(defn first-is-gone
  [l]
  (not (some #{(first l)} (vec (rest l)))))

(deftest bad-remove
  (testing "For all lists L, if we remove the first element E, E should not
           longer be in the list. (This is a false assumption)"
           (is (false?
                 (let [v (gen/vector gen/int)]
                   (:result (sc/quick-check 100 first-is-gone [v])))))))

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
                  (let [result (sc/quick-check 100 exception-thrower [gen/int])]
                    [(:result result) (get-in result [:shrunk :smallest])])))))

;; Tests are deterministic
;; ---------------------------------------------------------------------------

(defn vector-elements-are-unique
  [v]
  (== (count v) (count (distinct v))))

(defn unique-test
  [seed]
  (sc/quick-check 100 vector-elements-are-unique
                  [(gen/vector gen/int)] :seed seed))

(defn equiv-runs
  [seed]
  (= (unique-test seed) (unique-test seed)))

(deftest tests-are-deterministic
  (testing "If two runs are started with the same seed, they should
           return the same results."
           (is (:result
                 (sc/quick-check 100 equiv-runs [gen/int])))))
