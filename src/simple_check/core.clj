(ns simple-check.core
  (:require [simple-check.generators :as gen]
            [simple-check.clojure-test :as ct]))

(declare shrink-loop failure)

(defn make-rng
  [seed]
  (if seed
    [seed (gen/random seed)]
    (let [non-nil-seed (System/currentTimeMillis)]
      [non-nil-seed (gen/random non-nil-seed)])))

(defn- complete
  [property num-trials seed]
  (ct/report-trial property num-trials num-trials)
  {:result true :num-tests num-trials :seed seed})

(defn quick-check
  "Tests `property` `num-tests` times.

  Examples:

      (def p (for-all [a gen/pos-int] (> (* a a) a)))
      (quick-check 100 p)
  "
  [num-tests property & {:keys [seed max-size] :or {max-size 200}}]
  (let [[created-seed rng] (make-rng seed)
        size-seq (gen/make-size-range-seq max-size)]
    (loop [so-far 0
           size-seq size-seq]
      (if (== so-far num-tests)
        (complete property num-tests created-seed)
        (let [[size & rest-size-seq] size-seq
              result-map-rose (gen/call-gen property rng size)
              result-map (gen/rose-root result-map-rose)
              result (:result result-map)
              args (:args result-map)]
          (cond
            (instance? Throwable result) (failure
                                           property result-map-rose
                                           so-far size)
            result (do
                     (ct/report-trial property so-far num-tests)
                     (recur (inc so-far) rest-size-seq))
            :default (failure property result-map-rose so-far size)))))))

(defn- smallest-shrink
  [total-nodes-visited depth smallest]
  {:total-nodes-visited total-nodes-visited
   :depth depth
   :result (:result smallest)
   :smallest (:args smallest)})

(defn not-falsey-or-exception?
  "True if the value is not falsy or an exception"
  [value]
  (and value (not (instance? Throwable value))))

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
  [rose-tree]
  (let [shrinks-this-depth (gen/rose-children rose-tree)]
    (loop [nodes shrinks-this-depth
           current-smallest (gen/rose-root rose-tree)
           total-nodes-visited 0
           depth 0]
      (if (empty? nodes)
        (smallest-shrink total-nodes-visited depth current-smallest)
        (let [head (first nodes)
              tail (rest nodes)]
          (let [result (:result (gen/rose-root head))]
            (if (not-falsey-or-exception? result)
              ;; this node passed the test, so now try testing it's right-siblings
              (recur tail current-smallest (inc total-nodes-visited) depth)
              ;; this node failed the test, so check if it has children,
              ;; if so, traverse down them. If not, save this as the best example
              ;; seen now and then look at the right-siblings
              ;; children
              (let [children (gen/rose-children head)]
                (if (empty? children)
                  (recur tail (gen/rose-root head) (inc total-nodes-visited) depth)
                  (recur children (gen/rose-root head) (inc total-nodes-visited) (inc depth)))))))))))

(defn- failure
  [property failing-rose-tree trial-number size]
  (let [root (gen/rose-root failing-rose-tree)
        result (:result root)
        failing-args (:args root)]

    (ct/report-failure property result trial-number failing-args)

    {:result result
     :failing-size size
     :num-tests (inc trial-number)
     :fail (vec failing-args)
     :shrunk (shrink-loop failing-rose-tree)}))
