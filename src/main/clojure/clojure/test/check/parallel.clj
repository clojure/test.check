;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.parallel
  "Some beginning infrastructure for testing a property in parallel.")

(defn flatten1
  "Flatten a sequence of sequences. flatten1 only removes one layer of nesting,
  unlike clojure.core/flatten, which removes all layers of sequential values.
  Unlike (apply concat ...), flatten1 is lazy."
  [coll]
  (let [helper
        (fn helper [xs ys]
          (lazy-seq
            (if-let [s (seq ys)]
              (cons (first ys) (helper xs (rest ys)))
              (when-let [s (seq xs)]
                (helper (rest xs) (first xs))))))]
    (helper coll nil)))

(defn execute
  "Given a sequence of functions, `fs`, return a lazy sequence of the results
  of `fs`, evaluated with maximum parallelism of `parallelism`."
  [parallelism fs]
  (let [[sparks potential] (split-at parallelism fs)
        futures (doall (map future-call sparks))
        step (fn step [futured xs]
               (lazy-seq
                 (if-let [s (seq xs)]
                   (cons (deref (first futured))
                         (step (-> futured
                                 next
                                 (concat [(future-call (first s))]))
                               (rest s)))
                   (map deref futured))))]
    (step futures potential)))
