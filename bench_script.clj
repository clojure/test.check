;; okay
(ns user
  (:require [criterium.core :refer :all]
            [clojure.test.check.random :as r])
  (:import [javax.crypto.spec SecretKeySpec]))

(def k (byte-array 16))
(def kspeck (SecretKeySpec. k "AES"))
(def in (byte-array 16))
(def out (byte-array 16))

(println "======= Builtin AES =======")
(let [f @#'r/aes]
  (bench (f kspeck in out)))

(println "======= Pasted AES =======")
(let [f @#'r/fast-aes]
  (bench (f k in out)))

(println "======= java.util.Random =======")
(let [r (java.util.Random. 42)]
  (bench (loop [i 256 sum 0]
           (if (zero? i)
             sum
             (recur (dec i) (bit-xor sum (.nextLong r)))))))

(println "======= AES generation =======")
(let [r (r/make-random 42)]
  (bench (loop [i 256, sum 0, r r]
           (if (zero? i)
             sum
             (let [[r1 r2] (r/split r)]
               (recur (dec i) (bit-xor sum (r/rand-long r1)) r2))))))
