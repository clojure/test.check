(ns clojure.test.check.growth-test
  (:require [clojure.test.check.generators :as gen]))

(defn hash-map-with-extra-keys
  [& args]
  (gen/fmap #(apply merge %)
            (gen/tuple (gen/scale #(/ % 10) (gen/map gen/keyword gen/any))
                       (apply gen/hash-map args))))

(def bunch-of-business-records-and-stuff
  (gen/vector
   (hash-map-with-extra-keys
    :username gen/string-ascii
    :age      gen/large-integer
    :id       gen/large-integer
    :tweets   (gen/vector
               (hash-map-with-extra-keys
                :text gen/string-ascii
                :id gen/large-integer)))))

;; do we have a bug where gen/double breaks for double `size`?
;; yes. though I recall some intentional "size should always be an
;; integer" thoughts.

(for [size (range 0 40 10)]
  (->> (gen/generate bunch-of-business-records-and-stuff size)
       (tree-seq coll? seq)
       (count)))

(for [size (range 0 200 10)]
  (->> (gen/generate gen/any size)
       (tree-seq coll? seq)
       (count)))

;; (1 8 1 2 37 5 4 2 8 1 2 5 15 31 21 47 11 1 36 7)
;; (1 1 12 18 1 1 8 8 5 12 1 14 1 22 4 147 15 11 1 1)
;; (1 1 5 9 1 1 32 6 1 1 22 22 29 34 43 6 80 1 15 196)
;; (1 1 1 3 1 5 25 1 3 1 1 1 1 16 36 181 2 10 97 16)
;; (1 4 1 5 7 1 1 38 47 1 3 24 18 1 1 19 1 26 1 44)
;; (1 1 4 8 3 10 9 36 1 20 44 4 53 1 89 18 1 47 36 4)
;; (1 2 2 34 1 37 17 16 4 1 1 70 1 3 1 1 46 1 1 34)
;; (1 4 5 8 2 24 14 6 7 39 4 54 1 1 1 131 20 63 19 42)

;; (1 1 20 1 28 16 6 6 44 12)
;; (1 2 10 8 2 36 59 21 13 12)
;; (1 2 1 19 29 5 1 15 29 6)
;; (1 1 16 4 17 1 17 49 1 21)
;; (1 1 13 12 1 2 27 1 44 30)
