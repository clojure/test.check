(ns simple-check.generators
  (:refer-clojure :exclude [int vector map]))

(defprotocol Generator
  (arbitrary [this] [this rand-seed size])
  (shrink [this value]))

(defn sample
  ([generator]
   (repeatedly #(arbitrary generator)))
  ([generator num-samples]
   (take num-samples (sample generator))))

(defn- shrink-index
  [coll index generator]
  (clojure.core/map (partial assoc coll index) (shrink generator (coll index))))

(defn tuple
  [generators]
  (reify Generator
    (arbitrary [this]
      (vec (clojure.core/map arbitrary generators)))
    (shrink [this value]
      (mapcat (partial apply shrink-index value)
              (map-indexed clojure.core/vector generators)))))

(defn- halfs
  [n]
  (take-while (partial not= 0) (iterate #(quot % 2) n)))

(defn int
  [max-int]
  (reify Generator
    (arbitrary [this]
      (rand-int max-int))
    (shrink [this integer]
      (clojure.core/map (partial - integer) (halfs integer)))))

(defn shrink-seq
  [gen s]
  (if (empty? s)
    s
    (let [head (first s)
          tail (rest s)]
      (concat [tail]
              (for [x (shrink-seq gen tail)] (cons head x))
              (for [y (shrink gen head)] (cons y tail))))))

(defn vector
  [gen max-size]
  (reify Generator
    (arbitrary [this]
      (vec (repeatedly (rand-int max-size) #(arbitrary gen))))
    (shrink [this v]
      (clojure.core/map vec (shrink-seq gen v)))))

(defn- subvecs
  [v]
  (for [index (range 1 (count v))]
    (subvec v index)))

(defn- shrink-vecs
  [vs inner-gen]
  (clojure.core/map #(clojure.core/map
          (fn [v]
            (->> v
              (shrink inner-gen)
              first))
          %)
       vs))

(defn- safe-first
  [s]
  (if (seq? s)
    (first s)
    []))

(defn map
  [key-gen val-gen max-num-keys]
  (reify Generator
    (arbitrary [this]
      (into {} (repeatedly (rand-int max-num-keys)
                           (fn [] [(arbitrary key-gen)
                                   (arbitrary val-gen)]))))))
