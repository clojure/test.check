(ns simple-check.generators
  (:require [simple-check.util :as util])
  (:refer-clojure :exclude [int vector map]))

(defprotocol Shrink
  (shrink [this]))

(defn sample
  ([generator]
   (repeatedly #(util/nullary-apply generator)))
  ([generator num-samples]
   (take num-samples (sample generator))))

(defn shrink-index
  [coll index]
  (clojure.core/map (partial assoc coll index) (shrink (coll index))))

(defn tuple
  [generators]
  (fn [rand-seed size]
    (vec (clojure.core/map util/nullary-apply generators))))

(defn shrink-tuple
  [value]
  (mapcat (partial shrink-index value) (range (count value))))

(defn halfs
  [n]
  (take-while (partial not= 0) (iterate #(quot % 2) n)))

(defn int
  ([] (int :fake 10000))
  ([rand-seed size]
  (rand-int size)))

(defn shrink-int
  [integer]
  (clojure.core/map (partial - integer) (halfs integer)))

(defn shrink-seq
  [coll]
  (if (empty? coll)
    coll
    (let [head (first coll)
          tail (rest coll)]
      (concat [tail]
              (for [x (shrink-seq tail)] (cons head x))
              (for [y (shrink head)] (cons y tail))))))

(defn vector
  [gen]
  (fn [rand-seed size]
    (vec (repeatedly (rand-int size) #(util/nullary-apply gen)))))

(defn shrink-vector
  [value]
  (clojure.core/map vec (shrink-seq value)))

(defn map
  [key-gen val-gen]
  (fn [rand-seed size]
    (into {} (repeatedly (rand-int size)
                         (fn [] [(util/nullary-apply key-gen)
                                 (util/nullary-apply val-gen)])))))

(defn shrink-map
  [value]
  [])

;; Instances
;; ---------------------------------------------------------------------------

(extend java.lang.Number
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink shrink-int})

(extend clojure.lang.IPersistentVector
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink shrink-seq})

(extend clojure.lang.IPersistentMap
  Shrink
  ;; TODO:
  ;; this shrink goes into an infinite loop with floats
  {:shrink shrink-map})
