(ns simple-check.core
  (:require [simple-check.generators :as gen]
            [clojure.test :as ct]))

(defn- run-test
  [property args]
  (let [vars (map gen/arbitrary args)
        result (try
                 (apply property vars)
                 (catch Throwable t t))]
    [result vars]))

(declare shrink-loop failure report-trial)

(defn quick-check
  [num-tests property-fun & args]
  (loop [so-far 0]
    (if (== so-far num-tests)
      (do
        (report-trial property-fun so-far num-tests)
        {:result true :num-tests so-far})
      (let [[result vars] (run-test property-fun args)]
        (cond
          (instance? Throwable result) (failure property-fun result so-far args vars)
          result (do
                   (report-trial property-fun so-far num-tests)
                   (recur (inc so-far)))
          :default (failure property-fun result so-far args vars))))))

(defmacro forall [bindings expr]
  `(let [~@bindings]
     ~expr))

(defn- shrink-loop
  "Shrinking a value produces a sequence of smaller values of the same type.
  Each of these values can then be shrunk. Think of this as a tree. We do a
  modified depth-first search of the tree:

  Do a non-exhaustive search for a deeper (than the root) failing example.
  Additional rules added to depth-first search:
  * If a node passes the property, you may continue searching at this depth,
  but not backtrack
  * If a node fails the property, search it's children
  The value returned is the left-most failing example at the depth where a
  passing example was found."
  [prop gen failing]
  (let [shrinks (gen/shrink gen failing)]
    (loop [nodes shrinks
           f failing
           total-nodes-visited 0
           depth 0
           can-set-new-best? true]
      (if (empty? nodes)
        {:total-nodes-visited total-nodes-visited
         :depth depth
         :smallest f}
        (let [[head & tail] nodes]
          (if (try
                (apply prop head)
                (catch Throwable t
                  ; assuming that this `t` is of the same type that was
                  ; originally thrown in quick-check...
                  false))
            ;; this node passed the test, so now try testing it's right-siblings
            (recur tail f (inc total-nodes-visited) depth can-set-new-best?)
            ;; this node failed the test, so check if it has children,
            ;; if so, traverse down them. If not, save this as the best example
            ;; seen now and then look at the right-siblings
            ;; children
            (let [children (gen/shrink gen head)]
              (if (empty? children)
                (recur tail head (inc total-nodes-visited) depth false)
                (recur children head (inc total-nodes-visited) (inc depth) true)))))))))

(defn- report-trial
  [property-fun so-far num-tests]
  (ct/report {:type ::trial
              ::property property-fun
              ::trial [so-far num-tests]}))

(defn- failure
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
              ::params (vec failing-params)})
  (let [ret {:result result
             :num-tests trial-number
             :fail (vec failing-params)}]
    (assoc ret :shrunk (shrink-loop property-fun
                                    (gen/tuple args)
                                    (vec failing-params)))))

;; clojure.test reporting -----------------------------------------------------

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

;; Generators -----------------------------------------------------------------

