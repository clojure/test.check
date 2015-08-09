(ns clojure.test.check.random-test
  "Testing that the cljs impl matches the clojure impl."
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.test.check.random :as random]))

(deftest longs-test

  ;; comparing with this code run on clj-jvm:
  (comment
    (-> 42
        (random/make-java-util-splittable-random)
        (random/split-n 17)
        (->> (mapcat random/split)
             (map random/rand-long)
             (reduce bit-xor))
        (str))
    =>
    "5298131359241775269")

  (is (= "5298131359241775269"
         (-> 42
             (random/make-java-util-splittable-random)
             (random/split-n 17)
             (->> (mapcat random/split)
                  (map random/rand-long)
                  (reduce #(.xor %1 %2)))
             (str)))))

(deftest doubles-test

  ;; comparing with this code run on clj-jvm:
  (comment

    (-> -42
        (random/make-java-util-splittable-random)
        (random/split-n 17)
        (->> (mapcat random/split)
             (map random/rand-double)
             (reduce +))
        (str))
    =>
    "17.39141655134964")

  (is (= "17.39141655134964"
         (-> -42
             (random/make-java-util-splittable-random)
             (random/split-n 17)
             (->> (mapcat random/split)
                  (map random/rand-double)
                  (reduce +))
             (str)))))

(deftest auto-seeding-test
  (is (distinct? (random/rand-double (random/make-random))
                 (random/rand-double (random/make-random))
                 (random/rand-double (random/make-random))
                 (random/rand-double (random/make-random)))
      "Each call to make-random should return a different RNG."))
