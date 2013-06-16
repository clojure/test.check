(ns simple-check.properties
  (:require [simple-check.generators :as gen]))

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
