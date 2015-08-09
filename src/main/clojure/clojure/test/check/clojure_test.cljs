;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.clojure-test
  (:require-macros clojure.test.check.clojure-test)
  (:require [cljs.test :as ct :include-macros true]))

(defn- assert-check
  [{:keys [result] :as m}]
  (prn m)
  (if (instance? js/Error result)
    (throw result)
    (ct/is result)))

(def ^:dynamic *default-test-count* 100)

(defn process-options
  {:no-doc true}
  [options]
  (cond (nil? options) {:num-tests *default-test-count*}
        (number? options) {:num-tests options}
        (map? options) (if (:num-tests options)
                         options
                         (assoc options :num-tests *default-test-count*))
        :else (throw (ex-info (str "Invalid defspec options: " (pr-str options))
                              {:bad-options options}))))

(def ^:dynamic *report-trials*
  "Controls whether property trials should be reported via clojure.test/report.
  Valid values include:

  * false - no reporting of trials (default)
  * a function - will be passed a clojure.test/report-style map containing
  :clojure.test.check/property and :clojure.test.check/trial slots
  * true - provides quickcheck-style trial reporting (dots) via
  `trial-report-dots`

  (Note that all reporting requires running `quick-check` within the scope of a
  clojure.test run (via `test-ns`, `test-all-vars`, etc.)

  Reporting functions offered by clojure.test.check include `trial-report-dots` and
  `trial-report-periodic` (which prints more verbose trial progress information
  every `*trial-report-period*` milliseconds."
  false)

(def ^:dynamic *report-shrinking*
  "If true, a verbose report of the property being tested, the
  failing return value, and the arguments provoking that failure is emitted
  prior to the start of the shrinking search."
  false)

(def ^:dynamic *trial-report-period*
  "Milliseconds between reports emitted by `trial-report-periodic`."
  10000)

(def ^:private last-trial-report (atom 0))

(let [begin-test-var-method (get-method ct/report [::ct/default :begin-test-var])]
  (defmethod ct/report [::ct/default :begin-test-var] [m]
    (reset! last-trial-report (.valueOf (js/Date.)))
    (when begin-test-var-method (begin-test-var-method m))))

(defn- get-property-name
  [{property-fun ::property :as report-map}]
  (or (-> property-fun meta :name) (ct/testing-vars-str report-map)))

(defn trial-report-periodic
  "Intended to be bound as the value of `*report-trials*`; will emit a verbose
  status every `*trial-report-period*` milliseconds, like this one:

  Passing trial 3286 / 5000 for (your-test-var-name-here) (:)"
  [m]
  (let [t (.valueOf (js/Date.))]
    (when (> (- t *trial-report-period*) @last-trial-report)
      (println "Passing trial" (-> m ::trial first) "/" (-> m ::trial second)
        "for" (get-property-name m))
      (reset! last-trial-report t))))

(defn trial-report-dots
  "Intended to be bound as the value of `*report-trials*`; will emit a single
  dot every 1000 trials reported."
  [{[so-far total] ::trial}]
  (when (pos? so-far)
    (when (zero? (mod so-far 1000))
      (print ".")
      (flush))
    (when (== so-far total) (println))))

(defmethod ct/report [::ct/default ::trial] [m]
  (when-let [trial-report-fn (and *report-trials*
                                  (if (true? *report-trials*)
                                    trial-report-dots
                                    *report-trials*))]
    (trial-report-fn m)))

(defmethod ct/report [::ct/default ::shrinking] [m]
  (when *report-shrinking*
    (println "Shrinking" (get-property-name m)
             "starting with parameters" (pr-str (::params m)))))

(defn report-trial
  [property-fun so-far num-tests]
  (ct/report {:type ::trial
              ::property property-fun
              ::trial [so-far num-tests]}))

(defn report-failure
  [property-fun result trial-number failing-params]
  ;; TODO this is wrong, makes it impossible to clojure.test quickchecks that
  ;; should fail...
  #_(ct/report (if (instance? Throwable result)
                 {:type :error
                  :message (.getMessage result)
                  :actual result}
                 {:type :fail
                  :expected true
                  :actual result}))
  (ct/report {:type ::shrinking
              ::property property-fun
              ::params (vec failing-params)}))

