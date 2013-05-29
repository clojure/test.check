(ns simple-check.core-test
  (:use clojure.test
        [simple-check.core :as sc]))

;; plus and 0 form a monoid
;; ---------------------------------------------------------------------------

(defn passes-monoid-properties
  [a b c]
  (and (= (+ 0 a) a)
       (= (+ a 0) a)
       (= (+ a (+ b c)) (+ (+ a b) c))))

(deftest plus-and-0-are-a-monoid
  (testing "+ and 0 form a monoid"
           (is (let [a (sc/gen-int Integer/MAX_VALUE)]
                 (:result
                   (sc/quick-check 100 passes-monoid-properties a a a))))))

;; reverse
;; ---------------------------------------------------------------------------

(defn reverse-equal?-helper
  [l]
  (let [r (vec (reverse l))]
    (and (= (count l) (count r))
         (= (seq l) (rseq r)))))

(deftest reverse-equal?
  (testing ""
           (is (let [g (sc/gen-int 100)
                     v (sc/gen-vec g 100)]
                 (:result (sc/quick-check 100 reverse-equal?-helper v))))))

;; failing reverse
;; ---------------------------------------------------------------------------

(deftest bad-reverse-test
  (testing "bad reverse implementation fails"
           (is (= false
                  (let [g (sc/gen-int 100)
                        v (sc/gen-vec g 100)]
                    (:result (sc/quick-check 100 #(= (reverse %) %) v)))))))

;; failing element remove
;; ---------------------------------------------------------------------------

(defn first-is-gone
  [l]
  (not (some #{(first l)} (vec (rest l)))))

(deftest bad-remove
  (testing "foo"
           (is (= false
                  (let [g (sc/gen-int 100)
                        v (sc/gen-vec g 100)]
                    (:result (sc/quick-check 100 first-is-gone v)))))))
