;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.random
  "Purely functional and splittable pseudo-random number generators based on
  http://publications.lib.chalmers.se/records/fulltext/183348/local_183348.pdf."
  (:import [java.nio ByteBuffer]
           [java.util Arrays]
           [javax.crypto Cipher]
           [javax.crypto.spec SecretKeySpec]))

(defprotocol IRandom
  (split [random]
    "Returns [rand1 rand2].

  Note: to maintain independence you should not call split and rand
  with the same argument.")
  (rand-long [random]
    "Returns a long.

  Note: to maintain independence you should not call split and rand
  with the same argument"))

(defn blowfish
  "Inputs and output are byte arrays."
  [key block]
  {:post [(= (count key) (count block) (count %))]}
  (let [c (Cipher/getInstance "Blowfish/CBC/NoPadding")
        k (SecretKeySpec. key "Blowfish")]
    (.init c Cipher/ENCRYPT_MODE k)
    (.doFinal c block)))

(defn aes
  "Inputs and output are byte arrays."
  [key block]
  {:post [(= (count key) (count block) (count %))]}
  ;; we should be able to separately test the cost of allocation/initialization
  ;; and the cost of the algo itself
  (let [c (Cipher/getInstance "AES/ECB/NoPadding")
        k (SecretKeySpec. key "AES")]
    (.init c Cipher/ENCRYPT_MODE k)
    (.doFinal c block)))

(defn ^:private set-bit
  "Returns a new byte array with the bit at the given index set to 1."
  [^bytes byte-array bit-index]
  (let [byte-array' (Arrays/copyOf byte-array (alength byte-array))
        byte-index (bit-shift-right bit-index 3) ;; divide by 8
        intrabyte-index (bit-and bit-index 7)
        the-byte (aget byte-array' byte-index)
        ;; some way to do this without special-casing 7?
        the-byte' (bit-or the-byte (case intrabyte-index
                                     7 -127
                                     (bit-shift-left 1 intrabyte-index)))]
    (doto byte-array'
      (aset byte-index (byte the-byte')))))

(def zero-bytes-16 (byte-array 16))

(deftype AESRandom [state ^bytes path ^int path-length]
  IRandom
  (split [random]
    (if (= 126 path-length)
      (let [state1 (aes state path)
            state2 (aes state (set-bit path 127))]
        [(AESRandom. state1 zero-bytes-16 0)
         (AESRandom. state2 zero-bytes-16 0)])
      (let [path-length' (inc path-length)]
        [(AESRandom. state path path-length')
         (AESRandom. state (set-bit zero-bytes-16 path-length) path-length')])))
  (rand-long [random]
    (let [bytes (aes state path)]
      (.getLong (doto (ByteBuffer/allocate 8)
                  (.put bytes 0 8)
                  (.flip))))))

(defn make-random
  "Given one or two long seeds, returns an object that can
  be used with the IRandom protocol."
  ([^long seed] (make-random seed seed))
  ([^long seed1 ^long seed2]
     (let [state (.array (doto (ByteBuffer/allocate 16)
                           (.putLong seed1)
                           (.putLong seed2)))]
       (->AESRandom state zero-bytes-16 0))))
