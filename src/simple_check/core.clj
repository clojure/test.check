(ns simple-check.core
  (:require [simple-check.generators :as gen]
            [simple-check.clojure-test :as ct]))

(defn- run-test
  [property args]
  (let [vars (map gen/arbitrary args)
        result (try
                 (apply property vars)
                 (catch Throwable t t))]
    [result vars]))

(declare shrink-loop failure)

(defn quick-check
  [num-tests property-fun & args]
  (loop [so-far 0]
    (if (== so-far num-tests)
      (do
        (ct/report-trial property-fun so-far num-tests)
        {:result true :num-tests so-far})
      (let [[result vars] (run-test property-fun args)]
        (cond
          (instance? Throwable result) (failure property-fun result so-far args vars)
          result (do
                   (ct/report-trial property-fun so-far num-tests)
                   (recur (inc so-far)))
          :default (failure property-fun result so-far args vars))))))

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
  [prop gen failing]
  (let [shrinks (gen/shrink gen failing)]
    (loop [nodes shrinks
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
            (let [children (gen/shrink gen head)]
              (if (empty? children)
                (recur tail head (inc total-nodes-visited) depth false)
                (recur children head (inc total-nodes-visited) (inc depth) true)))))))))

(defn- failure
  [property-fun result trial-number args failing-params] 
  (ct/report-failure property-fun result trial-number args failing-params)
  {:result result
   :num-tests trial-number
   :fail (vec failing-params)
   :shrunk (shrink-loop property-fun
                        (gen/tuple args)
                        (vec failing-params))})

