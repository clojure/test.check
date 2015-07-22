;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Gary Fredericks"
      :doc "Purely functional and splittable pseudo-random number generators."}
  cljs.test.check.random
  (:refer-clojure :exclude [+ * bit-count bit-xor unsigned-bit-shift-right])
  (:require [cljs.core :as core]
            [goog.math.Long :as long]))

(defprotocol IRandom
  (rand-long [rng]
    "Returns a random goog.math.Long based on the given immutable RNG.

  Note: to maintain independence you should not call more than one
  function in the IRandom protocol with the same argument")
  (rand-double [rng]
    "Returns a random double between 0.0 (inclusive) and 1.0 (exclusive)
  based on the given immutable RNG.

  Note: to maintain independence you should not call more than one
  function in the IRandom protocol with the same argument")
  (split [rng]
    "Returns two new RNGs [rng1 rng2], which should generate
  sufficiently independent random data.

  Note: to maintain independence you should not call more than one
  function in the IRandom protocol with the same argument")
  (split-n [rng n]
    "Returns a collection of `n` RNGs, which should generate
  sufficiently independent random data.

  Note: to maintain independence you should not call more than one
  function in the IRandom protocol with the same argument"))

;;
;; This is a port of the clojure-jvm port of
;; java.util.SplittableRandom, and should give identical results.
;;

(defn ^:private unsigned-bit-shift-right
  [x n]
  (.shiftRightUnsigned x n))

(defn ^:private +
  [x y]
  (.add x y))

(defn ^:private *
  [x y]
  (.multiply x y))

(defn ^:private bit-xor
  [x y]
  (.xor x y))

(def ^:private bit-count-lookup
  (let [arr (make-array 256)]
    (aset arr 0 0)
    (dotimes [i 256]
      (aset arr i (core/+ (aget arr (bit-shift-right i 1))
                          (bit-and i 1))))
    arr))

(defn ^:private bit-count
  "Returns a JS number (not a Long)"
  [x]
  (let [low (.-low_ x)
        high (.-high_ x)]
    (core/+ (aget bit-count-lookup (-> low  (bit-and 255)))
            (aget bit-count-lookup (-> low  (bit-shift-right 8) (bit-and 255)))
            (aget bit-count-lookup (-> low  (bit-shift-right 16) (bit-and 255)))
            (aget bit-count-lookup (-> low  (bit-shift-right 24) (bit-and 255)))
            (aget bit-count-lookup (-> high (bit-and 255)))
            (aget bit-count-lookup (-> high (bit-shift-right 8) (bit-and 255)))
            (aget bit-count-lookup (-> high (bit-shift-right 16) (bit-and 255)))
            (aget bit-count-lookup (-> high (bit-shift-right 24) (bit-and 255))))))

(defn ^:private bxoubsr
  "Performs (-> x (unsigned-bit-shift-right n) (bit-xor x))."
  [x n]
  (-> x (unsigned-bit-shift-right n) (bit-xor x)))

(defn ^:private hex-long
  [s]
  (long/fromString s 16))

(def ^:private mix-64-const-1
  (hex-long "bf58476d1ce4e5b9"))
(def ^:private mix-64-const-2
  (hex-long "94d049bb133111eb"))


(defn ^:private mix-64
  [n]
  (-> n
      (bxoubsr 30)
      (* mix-64-const-1)
      (bxoubsr 27)
      (* mix-64-const-2)
      (bxoubsr 31)))

(def ^:private mix-gamma-const-1
  (hex-long "ff51afd7ed558ccd"))
(def ^:private mix-gamma-const-2
  (hex-long "c4ceb9fe1a85ec53"))
(def ^:private mix-gamma-const-3
  (hex-long "aaaaaaaaaaaaaaaa"))
(defn ^:private mix-gamma
  [n]
  (-> n
      (bxoubsr 33)
      (* mix-gamma-const-1)
      (bxoubsr 33)
      (* mix-gamma-const-2)
      (bxoubsr 33)
      (.or long/ONE)
      (as-> z
          (cond-> z
            (> 24 (-> z
                      (bxoubsr 1)
                      (bit-count)))
            (bit-xor mix-gamma-const-3)))))

(def ^:private double-unit
  (loop [i 53 x 1]
    (if (zero? i)
      x
      (recur (dec i) (/ x 2)))))

(def ^:private FFFFFFFF 4294967296)

(deftype JavaUtilSplittableRandom [^long gamma ^long state]
  IRandom
  (rand-long [_]
    (-> state (+ gamma) (mix-64)))
  (rand-double [this]
    (let [x (-> this
                (rand-long)
                (unsigned-bit-shift-right 11))
          low-bits (.getLowBitsUnsigned x)
          high-bits (.getHighBits x)]
      (core/+ (core/* double-unit low-bits)
              (core/* double-unit high-bits FFFFFFFF))))
  (split [this]
    (let [state' (+ gamma state)
          state'' (+ gamma state')
          gamma' (mix-gamma state'')]
      [(JavaUtilSplittableRandom. gamma state'')
       (JavaUtilSplittableRandom. gamma' (mix-64 state'))]))
  (split-n [this n]
    (case n
      0 []
      1 [this]
      (let [n-dec (dec n)]
        (loop [state state
               ret (transient [])]
          (if (= n-dec (count ret))
            (-> ret
                (conj! (JavaUtilSplittableRandom. gamma state))
                (persistent!))
            (let [state' (+ gamma state)
                  state'' (+ gamma state')
                  gamma' (mix-gamma state'')
                  new-rng (JavaUtilSplittableRandom. gamma' (mix-64 state'))]
              (recur state'' (conj! ret new-rng)))))))))

(def ^:private golden-gamma
  (hex-long "9e3779b97f4a7c15"))

(defn make-java-util-splittable-random
  [seed]
  (let [seed (cond (number? seed)
                   (long/fromNumber seed)

                   (instance? goog.math.Long seed)
                   seed

                   :else
                   (throw (ex-info "Bad seed type!" {:seed seed})))]
    (JavaUtilSplittableRandom. golden-gamma seed)))

(defn make-random
  "Given an optional integer (or goog.math.Long) seed, returns an
  implementation of the IRandom protocol."
  ([] (make-random (rand-int FFFFFFFF)))
  ([seed]
   (make-java-util-splittable-random seed)))
