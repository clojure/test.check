(ns simple-check.clojure-test-test
  (:use clojure.test)
  (:require [simple-check.generators :as gen]
            [simple-check.clojure-test :as ct :refer (defspec)]))

(defspec trial-counts 5000 (constantly true))

(defspec long-running-spec 1000 #(do (Thread/sleep 1) true))

(defn- vector-elements-are-unique
  [v]
  (== (count v) (count (distinct v))))

(defspec failing-spec 1000 vector-elements-are-unique (gen/vector (gen/int 100) 10))

(defn test-ns-hook
  []
  (is (= "{:test-var trial-counts, :result true, :num-tests 5000}\n"
         (with-out-str (binding [*test-out* *out*] (test-var #'trial-counts)))))
  (binding [ct/*report-trials* true]
     (is (= ".....\n{:test-var trial-counts, :result true, :num-tests 5000}\n"
            (with-out-str (test-var #'trial-counts)))))
  (binding [ct/*report-trials* ct/trial-report-periodic
            ct/*trial-report-period* 500]
    (is (re-seq
          #"(Passing trial \d{3} / 1000 for \(fn\* \[\] \(do \(Thread/sleep 1\) true\)\)\n)+"
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
          #"Shrinking vector-elements-are-unique starting with parameters \[\[.+"
          stdout))))

