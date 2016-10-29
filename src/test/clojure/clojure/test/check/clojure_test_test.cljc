;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.clojure-test-test
  (:require #?@(:cljs
                [[cljs.test :as test :refer [test-var] :refer-macros [is]]
                 [cljs.reader :refer [read-string]]])
            #?(:clj
               [clojure.test :as test :refer :all])
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop #?@(:cljs [:include-macros true])]
            [clojure.test.check.clojure-test :as ct #?@(:clj  [:refer (defspec)]
                                                        :cljs [:refer-macros (defspec)])]))

(defspec default-trial-counts
  (prop/for-all* [gen/int] (constantly true)))

(defspec trial-counts 5000
  (prop/for-all* [gen/int] (constantly true)))

(defspec long-running-spec 1000
  (prop/for-all* []
    #(do
       #?(:clj
          (Thread/sleep 1)
          :cljs
          (let [start (.valueOf (js/Date.))]
            ;; let's do some busy waiting for 1 msec, so we avoid setTimeout
            ;; which would make our test async
            (while (= start
                      (.valueOf (js/Date.)))
              (apply + (range 50)))))
       true)))

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
  (with-out-str #?(:clj  (binding [*test-out* *out*] (test-var v))
                   :cljs (test-var v))))

(defn ^:private capture-clojure-test-reports
  [func]
  (let [log (atom [])]
    (with-redefs [test/report #(swap! log conj %)]
      (func))
    @log))

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
      (is (re-matches #?(:clj  (java.util.regex.Pattern/compile "(?s)\\.{5}.+")
                         :cljs #"\.{5}[\s\S]+")
                      output))))

  (binding [ct/*report-trials* ct/trial-report-periodic
            ct/*trial-report-period* 500]
    (let [last-trial-report @#'ct/last-trial-report
          trial-report-0 @last-trial-report
          _ (test/report {:type :begin-test-var})
          trial-report-1 @last-trial-report]
      (is (> trial-report-1 trial-report-0)
          "calling with {:type :begin-test-var} makes last-trial-report to increment")
      (test/report {:type :end-test-var})
      (is (= trial-report-1 @last-trial-report)
          "calling with other :type keeps last-trial-report constant")
      (is (re-seq
           #"(Passing trial \d{3} / 1000 for .+\n)+"
           (capture-test-var #'long-running-spec)))
      (is (> @last-trial-report trial-report-1)
          "running the test makes last-trial-report to increment")))

  (let [[report-counters stdout]
        #?(:clj
           (binding [ct/*report-shrinking* true
                     ;; need to keep the failure of this-is-supposed-to-fail from
                     ;; affecting the clojure.test.check test run
                     *report-counters* (ref *initial-report-counters*)]
             (let [out (capture-test-var #'this-is-supposed-to-fail)]
               [@*report-counters* out]))

           :cljs
           (binding [ct/*report-shrinking* true]
             ;; need to keep the failure of this-is-supposed-to-fail from
             ;; affecting the clojure.test.check test run
             (let [restore-env    (test/get-current-env)
                   _              (test/set-env! (test/empty-env))
                   report-str     (capture-test-var #'this-is-supposed-to-fail)
                   env            (test/get-current-env)]
               (test/set-env! restore-env)
               [(:report-counters env) report-str])))]
    (is (== 1 (:fail report-counters)))
    (is (re-seq
         #?(:clj
            (java.util.regex.Pattern/compile "(?s)Shrinking vector-elements-are-unique starting with parameters \\[\\[.+")

            :cljs
            #"Shrinking vector-elements-are-unique starting with parameters \[\[[\s\S]+")
         stdout)))

  ;;
  ;; Test for TCHECK-118
  ;;
  (let [{trial ::ct/trial, shrinking ::ct/shrinking, shrunk ::ct/shrunk}
        (group-by :type (capture-clojure-test-reports this-is-supposed-to-fail))]
    ;; should have had some successful runs because the initial size
    ;; is too small for duplicates
    (is (seq trial))

    (is (= 1 (count shrinking)))
    (is (not (-> shrinking first ::ct/params first (->> (apply distinct?)))))

    (is (= 1 (count shrunk)))
    (let [[a b & more] (-> shrunk first ::ct/params first)]
      (is (empty? more))
      (is (and a b (= a b))))))
