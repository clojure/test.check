;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns cljs.test.check.rose-tree-test
  (:require [cljs.test :as test :include-macros true]
            [cljs.test.check :as tc]
            [cljs.test.check.generators :as gen]
            [cljs.test.check.properties :as prop :include-macros true]
            [cljs.test.check.rose-tree :as rose]
            [cljs.test.check.cljs-test :as ct :refer-macros [defspec]]))

(defn depth-one-children
  [[root children]]
  (into [] (map rose/root children)))

(defn depth-one-and-two-children
  [[root children]]
  (into []
    (concat
      (map rose/root children)
      (map rose/root (mapcat rose/children children)))))

(defspec test-collapse-rose
  100
  (prop/for-all [i gen/int]
    (let [tree (gen/int-rose-tree i)]
      (= (depth-one-and-two-children tree)
         (depth-one-children (rose/collapse tree))))))
