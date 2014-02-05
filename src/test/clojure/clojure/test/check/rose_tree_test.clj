(ns clojure.test.check.rose-tree-test
  (:use clojure.test)
  (:require [clojure.test.check.core       :as sc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :as ct :refer (defspec)]))

(defn depth-one-children
  [[root children]]
  (into [] (map gen/rose-root children)))

(defn depth-one-and-two-children
  [[root children]]
  (into []
        (concat (map gen/rose-root children)
                (map gen/rose-root (mapcat gen/rose-children children)))))

(defspec test-collapse-rose
  100
  (prop/for-all [i gen/int]
                (let [tree (#'gen/int-rose-tree i)]
                  (= (depth-one-and-two-children tree)
                     (depth-one-children (gen/collapse-rose tree))))))
