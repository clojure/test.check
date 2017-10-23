;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Gary Fredericks"
      :doc "Internal namespace, wrapping some goog.math.Long functionality."}
 clojure.test.check.random.longs
  (:refer-clojure :exclude [+ * bit-xor bit-or bit-count
                            unsigned-bit-shift-right])
  (:require [clojure.test.check.random.longs.bit-count-impl :as bit-count]
            [goog.math.Long :as long]
            [clojure.core :as core]))

(defn unsigned-bit-shift-right
  [x n]
  (.shiftRightUnsigned x n))

(defn +
  [x y]
  (.add x y))

(defn *
  [x y]
  (let [a48 (bit-shift-right-zero-fill (.-high_ x) 16)
        a32 (bit-and (.-high_ x) 0xFFFF)
        a16 (bit-shift-right-zero-fill (.-low_ x) 16)
        a00 (bit-and (.-low_ x) 0xFFFF)

        b48 (bit-shift-right-zero-fill (.-high_ y) 16)
        b32 (bit-and (.-high_ y) 0xFFFF)
        b16 (bit-shift-right-zero-fill (.-low_ y) 16)
        b00 (bit-and (.-low_ y) 0xFFFF)

        arr (array 0 0 0 0)]                              ;[c00 c16 c32 c48]
    (aset arr 0 (core/* a00 b00))                                                  ;c00 += a00 * b00;
    (aset arr 1 (bit-shift-right-zero-fill (aget arr 0) 16))                       ;c16 += c00 >>> 16
    (aset arr 0 (bit-and (aget arr 0) 0xFFFF))                                     ;c00 &= 0xFFFF;
    (aset arr 1 (core/+ (aget arr 1) (core/* a16 b00)))                            ;c16 += a16 * b00;
    (aset arr 2 (bit-shift-right-zero-fill (aget arr 1) 16))                       ;c32 += c16 >>> 16;
    (aset arr 1 (bit-and (aget arr 1) 0xFFFF))                                     ;c16 &= 0xFFFF;
    (aset arr 1 (core/+ (aget arr 1) (core/* a00 b16)))                            ;c16 += a00 * b16;
    (aset arr 2 (core/+ (aget arr 2) (bit-shift-right-zero-fill (aget arr 1) 16))) ;c32 += c16 >>> 16;
    (aset arr 1 (bit-and (aget arr 1) 0xFFFF))                                     ;c16 &= 0xFFFF;
    (aset arr 2 (core/+ (aget arr 2) (core/* a32 b00)))                            ;c32 += a32 * b00;
    (aset arr 3 (bit-shift-right-zero-fill (aget arr 2) 16))                       ;c48 += c32 >>> 16;
    (aset arr 2 (bit-and (aget arr 2) 0xFFFF))                                     ;c32 &= 0xFFFF;
    (aset arr 2 (core/+ (aget arr 2) (core/* a16 b16)))                            ;c32 += a16 * b16;
    (aset arr 3 (core/+ (aget arr 3) (bit-shift-right-zero-fill (aget arr 2) 16))) ;c48 += c32 >>> 16;
    (aset arr 2 (bit-and (aget arr 2) 0xFFFF))                                     ;c32 &= 0xFFFF;
    (aset arr 2 (core/+ (aget arr 2) (core/* a00 b32)))                            ;c32 += a00 * b32;
    (aset arr 3 (core/+ (aget arr 3) (bit-shift-right-zero-fill (aget arr 2) 16))) ;c48 += c32 >>> 16;
    (aset arr 2 (bit-and (aget arr 2) 0xFFFF))                                     ;c32 &= 0xFFFF;

    (aset arr 3 (core/+ (aget arr 3) (core/* a48 b00) (core/* a32 b16) (core/* a16 b32) (core/* a00 b48)))
    ;c48 += a48 * b00 + a32 * b16 + a16 * b32 + a00 * b48;
    (aset arr 3 (bit-and (aget arr 3) 0xFFFF))                                    ;c48 &= 0xFFFF;

    ;(c16 << 16) | c00, (c48 << 16) | c32
    (long/fromBits (core/bit-or (bit-shift-left (aget arr 1) 16) (aget arr 0))
                   (core/bit-or (bit-shift-left (aget arr 3) 16) (aget arr 2)))))

(defn bit-xor
  [x y]
  (.xor x y))

(defn bit-or
  [x y]
  (.or x y))

(defn from-string
  [s radix]
  (long/fromString s radix))

(defn from-number
  [x]
  (long/fromNumber x))

(defn ->long
  "Coerces to long, or returns nil if not possible."
  [x]
  (cond (number? x)
        (long/fromNumber x)

        (instance? goog.math.Long x)
        x))

(def ONE (long/getOne))

(def bit-count bit-count/bit-count)
