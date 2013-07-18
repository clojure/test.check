(ns simple-check.clojure-test-test
  (:use clojure.test)
  (:require [simple-check.generators :as gen]
            [simple-check.properties :as prop]
            [simple-check.clojure-test :as ct :refer (defspec)]))

(defspec trial-counts 5000
  (prop/for-all* [gen/int] (constantly true)))

(defspec long-running-spec 1000
  (prop/for-all* [] #(do (Thread/sleep 1) true)))

(defn- vector-elements-are-unique*
  [v]
  (== (count v) (count (distinct v))))

(def ^:private vector-elements-are-unique
  (prop/for-all*
    [(gen/vector gen/int)]
    vector-elements-are-unique*))

(defspec failing-spec 100 vector-elements-are-unique)

(defn test-ns-hook
  []
  (is (-> (with-out-str (binding [*test-out* *out*] (test-var #'trial-counts)))
             read-string
             (select-keys [:test-var :result :num-tests])
             (= {:test-var 'trial-counts, :result true, :num-tests 5000})))
  
  (binding [ct/*report-trials* true]
     (let [output (with-out-str (test-var #'trial-counts))]
       (is (re-matches #"(?s)\.{5}.+" output))))

  (binding [ct/*report-trials* ct/trial-report-periodic
            ct/*trial-report-period* 500]
    (is (re-seq
          #"(Passing trial \d{3} / 1000 for .+\n)+"
           (with-out-str (binding [*test-out* *out*] (test-var #'long-running-spec))))))

  (let [[report-counters stdout]
        (binding [ct/*report-shrinking* true
                  ; need to keep the failure of failing-spec from affecting the
                  ; simple-check test run
                  *report-counters* (ref *initial-report-counters*)]
          [*report-counters*
           (with-out-str (binding [*test-out* *out*] (test-var #'failing-spec)))])]
    (is (== 1 (:fail @report-counters)))
    (is (re-seq
          #"(?s)Shrinking vector-elements-are-unique starting with parameters \[\[.+"
          stdout))))

