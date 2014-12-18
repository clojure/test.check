;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns cljs.test.check.rose-tree
  "A lazy tree data structure used for shrinking."
  (:refer-clojure :exclude [filter remove seq])
  (:require [cljs.core :as core]))

(defn- exclude-nth
  "Exclude the nth value in a collection."
  [n coll]
  (lazy-seq
    (when-let [s (core/seq coll)]
      (if (zero? n)
        (rest coll)
        (cons (first s)
              (exclude-nth (dec n) (rest s)))))))

(defn join
  "Turn a tree of trees into a single tree. Does this by concatenating
  children of the inner and outer trees."
  {:no-doc true}
  [[[inner-root inner-children] children]]
  [inner-root (concat (map join children)
                      inner-children)])

(defn root
  "Returns the root of a Rose tree."
  {:no-doc true}
  [[root _children]]
  root)

(defn children
  "Returns the children of the root of the Rose tree."
  {:no-doc true}
  [[_root children]]
  children)

(defn pure
  "Puts a value `x` into a Rose tree, with no children."
  {:no-doc true}
  [x]
  [x []])

(defn fmap
  "Applies functions `f` to all values in the tree."
  {:no-doc true}
  [f [root children]]
  [(f root) (map #(fmap f %) children)])

(defn bind
  "Takes a Rose tree (m) and a function (k) from
  values to Rose tree and returns a new Rose tree.
  This is the monadic bind (>>=) for Rose trees."
  {:no-doc true}
  [m k]
  (join (fmap k m)))

(defn filter
  "Returns a new Rose tree whose values pass `pred`. Values who
  do not pass `pred` have their children cut out as well.
  Takes a list of roses, not a rose"
  {:no-doc true}
  [pred [the-root children]]
  [the-root (map #(filter pred %)
              (core/filter #(pred (root %)) children))])

(defn permutations
  "Create a seq of vectors, where each rose in turn, has been replaced
  by its children."
  {:no-doc true}
  [roses]
  (apply concat
         (for [[rose index]
               (map vector roses (range))]
           (for [child (children rose)] (assoc roses index child)))))

(defn zip
  "Apply `f` to the sequence of Rose trees `roses`."
  {:no-doc true}
  [f roses]
  [(apply f (map root roses))
   (map #(zip f %)
        (permutations roses))])

(defn remove
  {:no-doc true}
  [roses]
  (concat
    (map-indexed (fn [index _] (exclude-nth index roses)) roses)
    (permutations (vec roses))))

(defn shrink
  {:no-doc true}
  [f roses]
  (if (core/seq roses)
    [(apply f (map root roses))
     (map #(shrink f %) (remove roses))]
    [(f) []]))

(defn collapse
  "Return a new rose-tree whose depth-one children
  are the children from depth one _and_ two of the input
  tree."
  {:no-doc true}
  [[root the-children]]
  [root (concat (map collapse the-children)
                (map collapse
                     (mapcat children the-children)))])

(defn- make-stack
  [children stack]
  (if-let [s (core/seq children)]
    (cons children stack)
    stack))

(defn seq
  "Create a lazy-seq of all of the (unique) nodes in a shrink-tree.
  This assumes that two nodes with the same value have the same children.
  While it's not common, it's possible to create trees that don't
  fit that description. This function is significantly faster than
  brute-force enumerating all of the nodes in a tree, as there will
  be many duplicates."
  [root]
  (let [helper (fn helper [[node children] seen stack]
                 (lazy-seq
                   (if-not (seen node)
                     (cons node
                           (if (core/seq children)
                             (helper (first children) (conj seen node) (make-stack (rest children) stack))
                             (when-let [s (core/seq stack)]
                               (let [f (ffirst s)
                                     r (rest (first s))]
                                 (helper f (conj seen node) (make-stack r (rest s)))))))
                     (when-let [s (core/seq stack)]
                       (let [f (ffirst s)
                             r (rest (first s))]
                         (helper f seen (make-stack r (rest s))))))))]
    (helper root #{} '())))
