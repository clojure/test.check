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
  [total-nodes-visited depth smallest-args]
  {:total-nodes-visited total-nodes-visited
   :depth depth
   :smallest smallest-args})

(defn- safe-apply-props
  [prop args]
  (try (apply prop args)
    (catch Throwable t
      ; assuming that this `t` is of the same type that was
      ; originally thrown in quick-check...
      false)))

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
  (let [shrinks-this-depth (gen/shrink-tuple failing)]
    (loop [nodes shrinks-this-depth
           f failing
           total-nodes-visited 0
           depth 0
           can-set-new-best? true]
      ; TODO why does this cause failures? (or (empty? nodes) (>= total-nodes-visited 10000))
      (if (empty? nodes)
        (smallest-shrink total-nodes-visited depth f)
        (let [[head & tail] nodes]
          (if (safe-apply-props prop head)
            ;; this node passed the test, so now try testing it's right-siblings
            (recur tail f (inc total-nodes-visited) depth can-set-new-best?)
            ;; this node failed the test, so check if it has children,
            ;; if so, traverse down them. If not, save this as the best example
            ;; seen now and then look at the right-siblings
            ;; children
            (let [children (gen/shrink-tuple head)]
              (if (empty? children)
                (recur tail head (inc total-nodes-visited) depth false)
                (recur children head (inc total-nodes-visited) (inc depth) true)))))))))

(defn- failure
  [property property-fun result trial-number size failing-params]
  (ct/report-failure property result trial-number failing-params)
  {:result result
   :failing-size size
   :num-tests trial-number
   :fail (vec failing-params)
   :shrunk (shrink-loop property-fun
                        (vec failing-params))})

