(ns simple-check.core)

(defprotocol Generator
  (arbitrary [this] [this rand-seed size])
  (shrink [this value]))

(defn sample
  ([generator]
   (repeatedly #(arbitrary generator)))
  ([generator num-samples]
   (take num-samples (sample generator))))

(defn- run-test
  [property args]
  (let [vars (map arbitrary args)
        result (try
                 (apply property vars)
                 (catch Throwable t t))]
    [result vars]))

(declare shrink-loop)
(declare tuple)

(defn quick-check
  [num-tests property-fun & args]
  (loop [so-far 0]
    (if (= so-far num-tests)
      {:result true :num-tests so-far}
      (let [[result vars] (run-test property-fun args)]
        (cond
          (instance? Throwable result) {:result result
                                        :num-tests so-far
                                        :fail (vec vars)
                                        :shrunk (shrink-loop property-fun
                                                             (tuple args)
                                                             (vec vars))}
          result (recur (inc so-far))
          :default {:result false
                    :num-tests so-far
                    :fail (vec vars)
                    :shrunk (shrink-loop property-fun
                                         (tuple args)
                                         (vec vars))})))))

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
  (let [shrinks (shrink gen failing)]
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
            (let [children (shrink gen head)]
              (if (empty? children)
                (recur tail head (inc total-nodes-visited) depth false)
                (recur children head (inc total-nodes-visited) (inc depth) true)))))))))

;; Generators -----------------------------------------------------------------

(defn shrink-index
  [tuple index generator]
  (map (partial assoc tuple index) (shrink generator (tuple index))))

(defn tuple
  [generators]
  (reify Generator
    (arbitrary [this]
      (vec (map arbitrary generators)))
    (shrink [this value]
      (mapcat (partial apply shrink-index value)
              (map-indexed vector generators)))))

(defn- halfs
  [n]
  (take-while (partial not= 0) (iterate #(quot % 2) n)))

(defn gen-int
  [max-int]
  (reify Generator
    (arbitrary [this]
      (rand-int max-int))
    (shrink [this integer]
      (map (partial - integer) (halfs integer)))))

(defn shrink-seq
  [gen s]
  (if (empty? s)
    s
    (let [head (first s)
          tail (rest s)]
      (concat [tail]
              (for [x (shrink-seq gen tail)] (cons head x))
              (for [y (shrink gen head)] (cons y tail))))))

(defn gen-vec
  [gen max-size]
  (reify Generator
    (arbitrary [this]
      (vec (repeatedly (rand-int max-size) #(arbitrary gen))))
    (shrink [this v]
      (map vec (shrink-seq gen v)))))

(defn subvecs
  [v]
  (for [index (range 1 (count v))]
    (subvec v index)))

(defn shrink-vecs
  [vs inner-gen]
  (map #(map
          (fn [v]
            (->> v
              (shrink inner-gen)
              first))
          %)
       vs))

(defn safe-first
  [s]
  (if (seq? s)
    (first s)
    []))

(defn map-gen
  [key-gen val-gen max-num-keys]
  (reify Generator
    (arbitrary [this]
      (into {} (repeatedly (rand-int max-num-keys)
                           (fn [] [(arbitrary key-gen)
                                   (arbitrary val-gen)]))))))
