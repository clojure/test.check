(ns simple-check.clojure-test
  (:require [clojure.test :as ct]))

(defn- assert-check
  [{:keys [result] :as m}]
  (println m)
  (if (instance? Throwable result)
    (throw result)
    (ct/is result)))

(defmacro defspec
  "Defines a new clojure.test test var that uses `quick-check` to verify
  [property] with the given [args] (should be a sequence of generators),
  [default-times] times by default.  You can call the function defined as [name]
  with no arguments to trigger this test directly (i.e.  without starting a
  wider clojure.test run), or with a single argument that will override
  [default-times]."
  [name default-times property & args]
  `(do
     ; consider my shame for introducing a cyclical dependency like this...
     ; Don't think we'll know what the solution is until simple-check
     ; integration with another test framework is attempted.
     (require 'simple-check.core)
     (defn ~(vary-meta name assoc :test
                       `#(#'assert-check (assoc (~name) :test-var (str '~name))))
       ([] (~name ~default-times))
       ([times#]
        (simple-check.core/quick-check
          times#
          (vary-meta ~property assoc :name (str '~property))
          ~@args)))))

(def ^:dynamic *report-trials*
  "Controls whether property trials should be reported via clojure.test/report.
  Valid values include:
  
  * false - no reporting of trials (default)
  * a function - will be passed a clojure.test/report-style map containing
      :simple-check.core/property and :simple-check.core/trial slots
  * true - provides quickcheck-style trial reporting (dots) via
      `trial-report-dots`

  (Note that all reporting requires running `quick-check` within the scope of a
  clojure.test run (via `test-ns`, `test-all-vars`, etc.)
  
  Reporting functions offered by simple-check include `trial-report-dots` and
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

(let [begin-test-var-method (get-method ct/report :begin-test-var)]
  (defmethod ct/report :begin-test-var [m]
    (reset! last-trial-report (System/currentTimeMillis))
    (when begin-test-var-method (begin-test-var-method m))))

(defn- get-property-name
  [{property-fun ::property :as report-map}]
  (or (-> property-fun meta :name) (ct/testing-vars-str report-map)))

(defn trial-report-periodic
  "Intended to be bound as the value of `*report-trials*`; will emit a verbose
  status every `*trial-report-period*` milliseconds, like this one:
  
  Passing trial 3286 / 5000 for (your-test-var-name-here) (:)"
  [m]
  (let [t (System/currentTimeMillis)]
    (when (> (- t *trial-report-period*) @last-trial-report)
      (ct/with-test-out
        (println "Passing trial" (-> m ::trial first) "/" (-> m ::trial second)
                 "for" (get-property-name m)))
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

(defmethod ct/report ::trial [m]
  (when-let [trial-report-fn (and *report-trials*
                                  (if (true? *report-trials*)
                                    trial-report-dots
                                    *report-trials*))]
    (trial-report-fn m)))

(defmethod ct/report ::shrinking [m]
  (when *report-shrinking*
    (ct/with-test-out
      (println "Shrinking" (get-property-name m) "starting with parameters" (::params m)))))

(defn report-trial
  [property-fun so-far num-tests]
  (ct/report {:type ::trial
              ::property property-fun
              ::trial [so-far num-tests]}))

(defn report-failure
  [property-fun result trial-number args failing-params]
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

