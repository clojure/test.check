;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.properties
  (:require [clojure.test.check.generators :as gen]))

(defn- apply-gen
  [function]
  (fn [args]
    (let [result (try (apply function args)
                   #?(:clj (catch java.lang.ThreadDeath t (throw t)))
                   (catch #?(:clj Throwable :cljs :default) t t))]
      {:result result
       :function function
       :args args})))

(defn for-all*
  "Creates a property (properties are also generators). A property
  is a generator that generates the result of applying the function
  under test with the realized arguments. Once realized, the arguments
  will be applied to `function` with `apply`.

  Example:

  (for-all* [gen/int gen/int] (fn [a b] (>= (+ a b) a)))
  "
  [args function]
  (gen/fmap
    (apply-gen function)
    (apply gen/tuple args)))

(defn- binding-vars
  [bindings]
  (map first (partition 2 bindings)))

(defn- binding-gens
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
