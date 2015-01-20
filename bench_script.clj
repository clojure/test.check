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
