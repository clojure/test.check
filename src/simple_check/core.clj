(ns simple-check.core
  (:require [simple-check.generators :as gen]
            [simple-check.clojure-test :as ct]))

;; TODO: this isn't used now, but might be useful
;; once we allow for overriding shrinking
(defn shrinks
  [{shrink-fn :shrink} value]
  (if shrink-fn
    (shrink-fn value)
    (gen/shrink value)))

(defn- run-test
  [property rng size]
  (try
    (gen/call-gen property rng size)
    (catch Throwable t t)))

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
              result-map (run-test property rng size)
              result (:result result-map)
              args (:args result-map)]
          (cond
            (instance? Throwable result) (failure property (:function result-map) result so-far size args)
            result (do
                     (ct/report-trial property so-far num-tests)
                     (recur (inc so-far) rest-size-seq))
            :default (failure property (:function result-map) result so-far size args)))))))

(defmacro forall [bindings expr]
  `(let [~@bindings]
     ~expr))

(defn- smallest-shrink
  [total-nodes-visited depth smallest-args smallest-result]
  {:total-nodes-visited total-nodes-visited
   :depth depth
   :result smallest-result
   :smallest smallest-args})

(defn- safe-apply-props
  [prop args]
  (try (apply prop args)
    (catch Throwable t t)))

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
  [prop failing failing-result]
  (let [shrinks-this-depth (gen/shrink failing)]
    (loop [nodes shrinks-this-depth
           f failing
           result failing-result
           total-nodes-visited 0
           depth 0]
      ; TODO why does this cause failures? (or (empty? nodes) (>= total-nodes-visited 10000))
      (if (empty? nodes)
        (smallest-shrink total-nodes-visited depth f failing-result)
        (let [[head & tail] nodes]
          (let [head-result (safe-apply-props prop head)]
            (if (not-falsey-or-exception? head-result)
              ;; this node passed the test, so now try testing it's right-siblings
              (recur tail f failing-result
                     (inc total-nodes-visited) depth)
              ;; this node failed the test, so check if it has children,
              ;; if so, traverse down them. If not, save this as the best example
              ;; seen now and then look at the right-siblings
              ;; children
              (let [children (gen/shrink head)]
                (if (empty? children)
                  (recur tail head head-result (inc total-nodes-visited)
                         depth)
                  (recur children head head-result (inc total-nodes-visited)
                         (inc depth)))))))))))

(defn- failure
  [property property-fun result trial-number size failing-params]
  (ct/report-failure property result trial-number failing-params)
  {:result result
   :failing-size size
   :num-tests trial-number
   :fail (vec failing-params)
   :shrunk (shrink-loop property-fun failing-params result)})

