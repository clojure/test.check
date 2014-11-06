;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.clojure-test-test
  (:use clojure.test)
  (:require [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :as ct :refer (defspec)]))

(defspec default-trial-counts
  (prop/for-all* [gen/int] (constantly true)))

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

(defspec this-is-supposed-to-fail 100 vector-elements-are-unique)

(defn- capture-test-var
  [v]
  (doto (with-out-str (binding [*test-out* *out*] (test-var v)))
    println))

(defn test-ns-hook
  []
  (is (-> (capture-test-var #'default-trial-counts)
          read-string
          :num-tests
          (= ct/*default-test-count*)))

  (is (-> (capture-test-var #'trial-counts)
          read-string
          (select-keys [:test-var :result :num-tests])
          (= {:test-var "trial-counts", :result true, :num-tests 5000})))

  (binding [ct/*report-trials* true]
     (let [output (capture-test-var #'trial-counts)]
       (is (re-matches #"(?s)\.{5}.+" output))))

  (binding [ct/*report-trials* ct/trial-report-periodic
            ct/*trial-report-period* 500]
    (is (re-seq
          #"(Passing trial \d{3} / 1000 for .+\n)+"
           (capture-test-var #'long-running-spec))))

  (let [[report-counters stdout]
        (binding [ct/*report-shrinking* true
                  ; need to keep the failure of this-is-supposed-to-fail from
                  ; affecting the clojure.test.check test run
                  *report-counters* (ref *initial-report-counters*)]
          [*report-counters* (capture-test-var #'this-is-supposed-to-fail)])]
    (is (== 1 (:fail @report-counters)))
    (is (re-seq
          #"(?s)Shrinking vector-elements-are-unique starting with parameters \[\[.+"
          stdout))))

