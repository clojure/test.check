(ns simple-check.properties
  (:require [simple-check.generators :as gen]))

;; The fields that should be returned from each test
;; run:
;;
;; pass?: boolean or nil (nil implies test was discarded)
;; expect: boolean or (maybe?) exception
;; values: the realized values during this test
;;
;;
;; These fields could be returned:
;;
;; interrupted?
;; stamp: haskell QC nomenclature for stats/values collected
;; callbacks: maybe this is where printing/clojure.test stuff goes?

(defn apply-gen
  [function]
  (fn [args]
    (fn [random-seed size]
      ;; since we need to capture the arguments for shrinking and reporting
      ;; purposes, perhaps here is were we could do that. Return a single-run
      ;; `result` map that contains something like:
      ;; {:pass true
      ;;  :args [0 false]}
      (apply function args))))

(defn forall
  [args function]
  (gen/bind (gen/tuple args)
            (apply-gen function)))
