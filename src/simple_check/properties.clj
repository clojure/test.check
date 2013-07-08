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
    [:gen (fn [random-seed size]
      (let [result (try (apply function args) (catch Throwable t t))]
        {:result result
         :shrink gen/shrink-tuple
         :function function
         :args args}))]))

(defn for-all*
  [args function]
  (gen/bind (gen/tuple args)
            (apply-gen function)))

(defn binding-vars
  [bindings]
  (map first (partition 2 bindings)))

(defn binding-gens
  [bindings]
  (map second (partition 2 bindings)))

(defmacro for-all
  [bindings expr]
  `(for-all* ~(vec (binding-gens bindings))
             (fn [~@(binding-vars bindings)]
               ~expr)))
