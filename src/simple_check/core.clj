(ns simple-check.core
  (:require [simple-check.generators :as gen]
            [simple-check.clojure-test :as ct]
            [simple-check.util :as util]))

;; TODO: this isn't used now, but might be useful
;; once we allow for overriding shrinking
(defn shrinks
  [{shrink-fn :shrink} value]
  (if shrink-fn
    (shrink-fn value)
    (gen/shrink value)))

(defn- run-test
  [property rng size args]
  (let [vars (vec (map #(% rng size) args))
        result (try
                 (apply property vars)
                 (catch Throwable t t))]
    [result vars]))

(declare shrink-loop failure)

(defn make-rng
  [seed]
  (if seed
    [seed (gen/random seed)]
    (let [non-nil-seed (System/currentTimeMillis)]
      [non-nil-seed (gen/random non-nil-seed)])))

(defn make-size-range-seq
  [max-size]
  (cycle (range 1 max-size)))

(defn quick-check
  [num-tests property-fun args {:keys [seed max-size] :or {max-size 200}}]
  (let [[created-seed rng] (make-rng seed)]
    (loop [so-far 0
           size-seq (make-size-range-seq max-size)]
    (if (== so-far num-tests)
      (do
        (ct/report-trial property-fun so-far num-tests)
        {:result true :num-tests so-far :seed created-seed})
      (let [[size & rest-size-seq] size-seq
            [result vars] (run-test property-fun rng size args)]
        (cond
          (instance? Throwable result) (failure property-fun result so-far size args vars)
          result (do
                   (ct/report-trial property-fun so-far num-tests)
                   (recur (inc so-far) rest-size-seq))
          :default (failure property-fun result so-far size args vars)))))))

(defmacro forall [bindings expr]
  `(let [~@bindings]
     ~expr))

(defn- shrink-loop
  "Shrinking a value produces a sequence of smaller values of the same type.
  Each of these values can then be shrunk. Think of this as a tree. We do a
  modified depth-first search of the tree:

  Do a non-exhaustive search for a deeper (than the root) failing example.
  Additional rules added to depth-first search:
  * If a node passes the property, you may continue searching at this depth,
  but not backtrack
  * If a node fails the property, search it's children
  The value returned is the left-most failing example at the depth where a
  passing example was found."
  [prop failing]
  (let [shrinks-this-depth (gen/shrink-tuple  failing)]
    (loop [nodes shrinks-this-depth
           f failing
           total-nodes-visited 0
           depth 0
           can-set-new-best? true]
      (if (empty? nodes)
        {:total-nodes-visited total-nodes-visited
         :depth depth
         :smallest f}
        (let [[head & tail] nodes]
          (if (try
                (apply prop head)
                (catch Throwable t
                  ; assuming that this `t` is of the same type that was
                  ; originally thrown in quick-check...
                  false))
            ;; this node passed the test, so now try testing it's right-siblings
            (recur tail f (inc total-nodes-visited) depth can-set-new-best?)
            ;; this node failed the test, so check if it has children,
            ;; if so, traverse down them. If not, save this as the best example
            ;; seen now and then look at the right-siblings
            ;; children
            (do
              (let [children (gen/shrink-tuple head)]
              (if (empty? children)
                (recur tail head (inc total-nodes-visited) depth false)
                (recur children head (inc total-nodes-visited) (inc depth) true))))))))))

(defn- failure
  [property-fun result trial-number size args failing-params]
  (ct/report-failure property-fun result trial-number args failing-params)
  {:result result
   :failing-size size
   :num-tests trial-number
   :fail (vec failing-params)
   :shrunk (shrink-loop property-fun
                        (vec failing-params))})

