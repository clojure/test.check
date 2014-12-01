;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.properties
  (:import clojure.test.check.generators.Generator)
  (:require [clojure.test.check.generators :as gen]))

(defrecord Result [result pass? message stamps])

(defprotocol ToResult
  (to-result [a]))

(extend java.lang.Object
  ToResult
  {:to-result (fn [b]
               ;; not checking for caught exceptions here
               (->Result b (not (false? b)) nil nil))})

(extend nil
  ToResult
  {:to-result (fn [b]
               (->Result b false nil nil))})

(extend java.lang.Boolean
  ToResult
  {:to-result (fn [b]
               (->Result b b nil nil))})

(extend Generator
  ToResult
  {:to-result identity})

(extend Result
  ToResult
  {:to-result identity})

(defn message
  [m property]
  (assoc property :message m))

(defn- apply-gen
  [function]
  (fn [args]
    (let [result (to-result (try (apply function args) (catch Throwable t t)))]
      (if (gen/generator? result)
        result
        {:result (:result result)
         :function function
         :args args}))))

(defn for-all*
  "Creates a property (properties are also generators). A property
  is a generator that generates the result of applying the function
  under test with the realized arguments. Once realized, the arguments
  will be applied to `function` with `apply`.

  Example:

  (for-all* [gen/int gen/int] (fn [a b] (>= (+ a b) a)))
  "
  [args function]
  (gen/bind
    (apply gen/tuple args)
    (fn [a]
      (let [result ((apply-gen function) a)]
        (cond (gen/generator? result)
              (gen/fmap (fn [r]
                          (update-in r [:args] #(vec (concat a %)))) result)
              :else (gen/return result))))
    ))

(defn binding-vars
  [bindings]
  (map first (partition 2 bindings)))

(defn binding-gens
  [bindings]
  (map second (partition 2 bindings)))

(defmacro for-all
  "Macro sugar for `for-all*`. `for-all` lets you name the parameter
  and use them in expression, without wrapping them in a lambda. Like
  `for-all*`, it returns a property.

  Examples

  (for-all [a gen/int
  b gen/int]
  (>= (+ a b) a))
  "
  [bindings & body]
  `(for-all* ~(vec (binding-gens bindings))
             (fn [~@(binding-vars bindings)]
               ~@body)))
