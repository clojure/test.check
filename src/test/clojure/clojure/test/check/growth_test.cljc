(ns clojure.test.check.growth-test
  (:require [clojure.test.check.generators :as gen]
            [clojure.test.check.random :as random]
            [criterium.core :as crit]))

#_
(def simple-type
  (one-of [int large-integer double char string ratio boolean keyword
           keyword-ns symbol symbol-ns uuid]))

(defn generate-with-entropy
  [gen size]
  (let [{:keys [rose entropy-used]} (gen/call-gen gen
                                                  (random/make-random)
                                                  size)
        x (clojure.test.check.rose-tree/root rose)]
    (if (instance? clojure.lang.IObj x)
      (vary-meta x assoc :entropy-used entropy-used)
      x)))

(defn hash-map-with-extra-keys
  [& args]
  (gen/fmap #(apply merge %)
            (gen/tuple (gen/scale #(/ % 10) (gen/map gen/keyword gen/any))
                       (apply gen/hash-map args))))

(def bunch-of-business-records-and-stuff
  (gen/vector
   (hash-map-with-extra-keys
    :username gen/string-ascii
    :age      gen/large-integer
    :id       gen/large-integer
    :tweets   (gen/vector
               (hash-map-with-extra-keys
                :text gen/string-ascii
                :id gen/large-integer)))))

(def can-this-be-reasonablized?
  (gen/map gen/any gen/any))

;; do we have a bug where gen/double breaks for double `size`?
;; yes. though I recall some intentional "size should always be an
;; integer" thoughts.

(comment
  (for [size (range 0 40 10)]
    (->> (gen/generate bunch-of-business-records-and-stuff size)
         (tree-seq coll? seq)
         (count)))

  (for [size (range 0 200 10)]
    (->> (gen/generate gen/any size)
         (tree-seq coll? seq)
         (count))))

;; (1 8 1 2 37 5 4 2 8 1 2 5 15 31 21 47 11 1 36 7)
;; (1 1 12 18 1 1 8 8 5 12 1 14 1 22 4 147 15 11 1 1)
;; (1 1 5 9 1 1 32 6 1 1 22 22 29 34 43 6 80 1 15 196)
;; (1 1 1 3 1 5 25 1 3 1 1 1 1 16 36 181 2 10 97 16)
;; (1 4 1 5 7 1 1 38 47 1 3 24 18 1 1 19 1 26 1 44)
;; (1 1 4 8 3 10 9 36 1 20 44 4 53 1 89 18 1 47 36 4)
;; (1 2 2 34 1 37 17 16 4 1 1 70 1 3 1 1 46 1 1 34)
;; (1 4 5 8 2 24 14 6 7 39 4 54 1 1 1 131 20 63 19 42)

;; (1 1 20 1 28 16 6 6 44 12)
;; (1 2 10 8 2 36 59 21 13 12)
;; (1 2 1 19 29 5 1 15 29 6)
;; (1 1 16 4 17 1 17 49 1 21)
;; (1 1 13 12 1 2 27 1 44 30)



;;
;; What's going on with gen/any?
;; It's sometimes slow even when it doesn't generate big things
;;

(comment

  (crit/quick-bench
   (gen/generate gen/any 200))
  ;; WARNING: Final GC required 3.043941722205168 % of runtime
  ;; WARNING: Final GC required 19.78034304849658 % of runtime
  ;; Evaluation count : 12 in 6 samples of 2 calls.
  ;;              Execution time mean : 73.828667 ms
  ;;     Execution time std-deviation : 89.049219 ms
  ;;    Execution time lower quantile : 1.589576 ms ( 2.5%)
  ;;    Execution time upper quantile : 187.558049 ms (97.5%)
  ;;                    Overhead used : 1.570969 ns 328

  (crit/quick-bench
   (gen/generate (gen/recursive-gen gen/vector gen/nat) 200))
  ;; WARNING: Final GC required 24.4165940427655 % of runtime
  ;; Evaluation count : 1692 in 6 samples of 282 calls.
  ;;              Execution time mean : 411.478227 µs
  ;;     Execution time std-deviation : 53.461595 µs
  ;;    Execution time lower quantile : 356.991624 µs ( 2.5%)
  ;;    Execution time upper quantile : 490.133988 µs (97.5%)
  ;;                    Overhead used : 1.570969 ns

  (crit/quick-bench
   (gen/generate (gen/recursive-gen gen/container-type gen/nat) 200))
  ;; WARNING: Final GC required 34.39580194827289 % of runtime
  ;; Evaluation count : 96 in 6 samples of 16 calls.
  ;;              Execution time mean : 4.995341 ms
  ;;     Execution time std-deviation : 3.026917 ms
  ;;    Execution time lower quantile : 1.998821 ms ( 2.5%)
  ;;    Execution time upper quantile : 8.636470 ms (97.5%)
  ;;                    Overhead used : 1.570969 ns


  (crit/quick-bench
   (gen/generate (gen/recursive-gen gen/vector gen/simple-type) 200))
  ;; WARNING: Final GC required 27.01510065417733 % of runtime
  ;; Evaluation count : 30 in 6 samples of 5 calls.
  ;;              Execution time mean : 20.792677 ms
  ;;     Execution time std-deviation : 15.411949 ms
  ;;    Execution time lower quantile : 6.359935 ms ( 2.5%)
  ;;    Execution time upper quantile : 45.326464 ms (97.5%)
  ;;                    Overhead used : 1.570969 ns

  ;; Found 1 outliers in 6 samples (16.6667 %)
  ;; 	low-severe	 1 (16.6667 %)
  ;;  Variance from outliers : 83.0042 % Variance is severely inflated by outliers

  (crit/quick-bench
   (gen/generate (gen/recursive-gen gen/vector

                                    (gen/one-of
                                     [#_#_#_#_#_#_#_
                                      gen/int gen/large-integer gen/double
                                      gen/char gen/string gen/ratio gen/boolean
                                      ;; #_#_#_#_#_
                                      gen/keyword gen/keyword-ns gen/symbol
                                      gen/symbol-ns gen/uuid]))
                 200))


  [
   gen/int           ;; 0.376μs
   gen/large-integer ;; 3.4μs
   gen/double        ;; 9.1μs
   gen/char          ;; 0.427μs
   gen/string        ;; 76μs
   gen/ratio
   gen/boolean
   gen/keyword
   gen/keyword-ns
   gen/symbol
   gen/symbol-ns
   gen/uuid]
  )
