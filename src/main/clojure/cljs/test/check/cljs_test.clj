;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns cljs.test.check.cljs-test
  (:require [cljs.test :as ct]))

(defmacro defspec
  "Defines a new cljs.test test var that uses `quick-check` to verify
  [property] with the given [args] (should be a sequence of generators),
  [default-times] times by default.  You can call the function defined as [name]
  with no arguments to trigger this test directly (i.e., without starting a
  wider cljs.test run), with a single argument that will override
  [default-times], or with a map containing any of the keys
  [:seed :max-size :num-tests]."
  {:arglists '([name property] [name num-tests? property] [name options? property])}
  [name & args]
  (let [property           (second args)
        [options property] (if property
                             [(first args) property]
                             [nil (first args)])]
    `(do
       (defn ~(vary-meta name assoc
                         ::defspec true
                         :test `#(cljs.test.check.cljs-test/assert-check
                                   (assoc (~name) :test-var (str '~name))))
         ([] (let [options# (process-options ~options)]
               (apply ~name (:num-tests options#) (apply concat options#))))
         ([~'times & {:keys [~'seed ~'max-size] :as ~'quick-check-opts}]
            (apply
             cljs.test.check/quick-check
             ~'times
             (vary-meta ~property assoc :name (str '~property))
             (apply concat ~'quick-check-opts)))))))
