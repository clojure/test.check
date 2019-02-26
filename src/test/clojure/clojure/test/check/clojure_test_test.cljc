;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.clojure-test-test
  (:require [clojure.set :as set]
            [clojure.string :as str]
            #?@(:cljs
                [[cljs.test
                  :as test
                  :include-macros true
                  :refer [test-var]
                  :refer-macros [is deftest testing]]
                 [cljs.reader :refer [read-string]]])
            #?(:clj
               [clojure.test :as test :refer :all])
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :as ct :refer [defspec]]))

(declare ^:dynamic test-report)

(defn capturing-report [reports m]
  (swap! reports conj m)
  (test-report m))

(defn ^:private capture-test-var
  "Returns map of :reports, :report-counters, :out, and :test-out."
  [v]
  (let [reports (atom [])]
    (binding [test-report test/report
              test/report (partial capturing-report reports)]
      #?(:clj
         (binding [*report-counters*   (ref *initial-report-counters*)
                   *test-out*          (java.io.StringWriter.)
                   *testing-contexts*  (list)
                   *testing-vars*      (list)]
           (let [out (with-out-str (test-var v))]
             {:reports @reports
              :report-counters @*report-counters*
              :out out
              :test-out (str *test-out*)}))
         :cljs
         (binding [test/*current-env* (test/empty-env)]
           (let [out (with-out-str (test-var v))]
             ;; cljs.test doesn't distinguish between *out* and *test-out*
             {:reports @reports
              :report-counters (:report-counters test/*current-env*)
              :out out
              :test-out out}))))))

(defspec default-trial-counts
  (prop/for-all* [gen/int] (constantly true)))

(deftest can-use-num-tests-default-value
  (let [{:keys [reports]} (capture-test-var #'default-trial-counts)
        num-tests (->> reports
                       (filter #(= ::ct/complete (:type %)))
                       first
                       ::ct/complete
                       :num-tests)]
    (is (= num-tests ct/*default-test-count*))))

(deftest tcheck-116-debug-prn-should-be-optional
  (testing "bind ct/*report-completion* to false to supress completion report"
    (binding [ct/*report-completion* false]
      (let [{:keys [out]} (capture-test-var #'default-trial-counts)]
        (is (= out "")))))

  (testing "report completions by default"
    (let [{:keys [out]} (capture-test-var #'default-trial-counts)
          completion    (-> out read-string (select-keys [:test-var :result :num-tests]))]
      (is (= completion {:test-var  "default-trial-counts"
                         :result    true
                         :num-tests ct/*default-test-count*})))))

(def trial-counts-num-tests 5000)
(defspec trial-counts trial-counts-num-tests
  (prop/for-all* [gen/int] (constantly true)))

(deftest can-specify-num-tests
  (let [{:keys [reports]} (capture-test-var #'trial-counts)
        num-tests (->> reports
                       (filter #(= ::ct/complete (:type %)))
                       first
                       ::ct/complete
                       :num-tests)]
    (is (= num-tests trial-counts-num-tests))))

(deftest can-report-completion-with-specified-num-tests
  (let [{:keys [out]} (capture-test-var #'trial-counts)
        completion (-> out read-string (select-keys [:test-var :result :num-tests]))]
    (is (= completion {:test-var  "trial-counts"
                       :result    true
                       :num-tests trial-counts-num-tests}))))

(deftest can-report-trials-with-dots
  (binding [ct/*report-trials* true]
    (let [{:keys [out]} (capture-test-var #'trial-counts)]
      (is (re-matches #?(:clj (java.util.regex.Pattern/compile "(?s)\\.{5}.+")
                         :cljs #"\.{5}[\s\S]+")
                      out)))))

(defspec long-running-spec 1000
  (prop/for-all*
    []
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

(defn wait-for-clock-tick
  "Allow time to progress to avoid timing issues with sub-millisecond code."
  [start]
  #?(:clj (Thread/sleep 1)
     :cljs (while (>= start (.valueOf (js/Date.)))
             (apply + (range 50)))))

(deftest can-report-trials-periodically
  (binding [ct/*report-trials* ct/trial-report-periodic
            ct/*trial-report-period* 500]
    (let [last-trial-report @#'ct/last-trial-report]

      (testing "test/report with {:type :begin-test-var} increments last-trial-report"
        (let [initial-trial-report @last-trial-report]
          (wait-for-clock-tick initial-trial-report)
          (test/report {:type :begin-test-var})
          (is (> @last-trial-report initial-trial-report))))

      (testing "test/report with other :type does not increment last-trial-report"
        (let [initial-trial-report @last-trial-report]
          (wait-for-clock-tick initial-trial-report)
          (test/report {:type :end-test-var})
          (is (= @last-trial-report initial-trial-report))))

      (testing "running the test increments last-trial-report"
        (let [initial-trial-report @last-trial-report]
          (wait-for-clock-tick initial-trial-report)
          (is (re-seq
                #"(Passing trial \d{3} / 1000 for long-running-spec\n)+"
                (:test-out
                 (capture-test-var #'long-running-spec))))
          (is (> @last-trial-report initial-trial-report)))))))

(defn- vector-elements-are-unique*
  [v]
  (== (count v) (count (distinct v))))

(def ^:private vector-elements-are-unique
  (prop/for-all*
   [(gen/vector gen/int)]
   vector-elements-are-unique*))

(defspec this-is-supposed-to-fail 100 vector-elements-are-unique)

(deftest can-report-failures
  (let [{:keys [test-out]} (capture-test-var #'this-is-supposed-to-fail)
        [result-line expected-line actual-line & more] (->> (str/split-lines test-out)
                                                            ;; skip any ::shrunk messages
                                                            (drop-while #(not (re-find #"^FAIL" %))))]
    (is (re-find #"^FAIL in \(this-is-supposed-to-fail\) " result-line))
    #?(:clj (is (re-find #"\(clojure_test_test\.cljc:\d+\)$" result-line)))
    (is (= expected-line "expected: {:result true}"))
    (let [actual (read-string (subs actual-line 10))]
      (is (set/subset? #{:result :result-data :seed :failing-size :num-tests :fail :shrunk}
                       (set (keys actual))))
      (is (= false (:result actual))))
    (is (nil? more))))

(deftest can-report-shrinking
  (testing "don't emit Shrinking messages by default"
    (let [{:keys [report-counters test-out]} (capture-test-var #'this-is-supposed-to-fail)]
      (is (== 1 (:fail report-counters)))
      (is (not (re-find #"Shrinking" test-out)))))

  (testing "bind *report-shrinking* to true to emit Shrinking messages"
    (binding [ct/*report-shrinking* true]
      (let [{:keys [report-counters test-out]} (capture-test-var #'this-is-supposed-to-fail)]
        (is (== 1 (:fail report-counters)))
        (is (re-seq #"Shrinking this-is-supposed-to-fail starting with parameters \[\[[\s\S]+"
                    test-out))))))

(deftest tcheck-118-pass-shrunk-input-on-to-clojure-test
  (let [{trial ::ct/trial, shrinking ::ct/shrinking, shrunk ::ct/shrunk}
        (group-by :type (:reports (capture-test-var #'this-is-supposed-to-fail)))]
    ;; should have had some successful runs because the initial size
    ;; is too small for duplicates
    (is (seq trial))

    (is (= 1 (count shrinking)))
    (is (not (-> shrinking first ::ct/params first (->> (apply distinct?)))))

    (is (= 1 (count shrunk)))
    (let [[a b & more] (-> shrunk first ::ct/params first)]
      (is (empty? more))
      (is (and a b (= a b))))))

(deftest can-report-shrunk
  (testing "supress shrunk report when ct/*report-completion* is bound to false"
    (binding [ct/*report-completion* false]
      (let [{:keys [test-out]} (capture-test-var #'this-is-supposed-to-fail)]
        (is (not (re-find #":type :clojure.test.check.clojure-test/shrunk" test-out))))))

  (testing "report shrunk by default"
    (let [{:keys [test-out]} (capture-test-var #'this-is-supposed-to-fail)]
      (is (re-find #":type :clojure.test.check.clojure-test/shrunk" test-out)))))

(defspec this-throws-an-exception
  (prop/for-all [x gen/nat]
    (throw (ex-info "this property is terrible" {}))))

(deftest can-re-throw-exceptions-to-clojure-test
  (let [{:keys [report-counters test-out]} (capture-test-var #'this-throws-an-exception)]
    (is (= report-counters {:test 1, :pass 0, :fail 0, :error 1}))
    (is (re-find #"ERROR in \(this-throws-an-exception\)" test-out))
    ;; TCHECK-151
    (is (= 1 (count (re-seq #"this property is terrible" test-out)))
        "Only prints exceptions twice")))


(defn test-ns-hook
  "Run only tests defined by deftest, ignoring those defined by defspec."
  []
  (let [tests (->> (vals (ns-interns #?(:clj (find-ns 'clojure.test.check.clojure-test-test)
                                        :cljs 'clojure.test.check.clojure-test-test)))
                   (filter #(let [m (meta %)]
                              (and (:test m)
                                   (not (::ct/defspec m)))))
                   (sort-by #(:line (meta %))))]
    (test/test-vars tests)))
