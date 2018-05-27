;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check
  (:require [clojure.test.check.generators :as gen]
            [clojure.test.check.random :as random]
            [clojure.test.check.results :as results]
            [clojure.test.check.rose-tree :as rose]
            [clojure.test.check.impl :refer [get-current-time-millis]]))

(declare shrink-loop failure)

(defn- make-rng
  [seed]
  (if seed
    [seed (random/make-random seed)]
    (let [non-nil-seed (get-current-time-millis)]
      [non-nil-seed (random/make-random non-nil-seed)])))

(defn- complete
  [property num-trials seed start-time reporter-fn]
  (let [time-elapsed-ms (- (get-current-time-millis) start-time)]
    (reporter-fn {:type :complete
                  :property property
                  :result true
                  :pass? true
                  :num-tests num-trials
                  :time-elapsed-ms time-elapsed-ms
                  :seed seed})
    {:result true
     :pass? true
     :num-tests num-trials
     :time-elapsed-ms time-elapsed-ms
     :seed seed}))


(defn ^:private legacy-result
  "Returns a value for the legacy :result key, which has the peculiar
  property of conflating returned exceptions with thrown exceptions."
  [result]
  (if (satisfies? results/Result result)
    (let [d (results/result-data result)]
      (if-let [[_ e] (find d :clojure.test.check.properties/error)]
        #?(:clj e
           :cljs (if (instance? js/Error e)
                   e
                   (ex-info "Non-Error object thrown in test"
                            {}
                            e)))
        (results/pass? result)))
    result))

(defn quick-check
  "Tests `property` `num-tests` times.

  Takes several optional keys:

  `:seed`
    Can be used to re-run previous tests, as the seed used is returned
    after a test is run.

  `:max-size`.
    can be used to control the 'size' of generated values. The size will
    start at 0, and grow up to max-size, as the number of tests increases.
    Generators will use the size parameter to bound their growth. This
    prevents, for example, generating a five-thousand element vector on
    the very first test.

  `:reporter-fn`
    A callback function that will be called at various points in the test
    run, with a map like:

      ;; called after a passing trial
      {:type            :trial
       :args            [...]
       :num-tests       <number of tests run so far>
       :num-tests-total <total number of tests to be run>
       :seed            42
       :pass?           true
       :property        #<...>
       :result          true
       :result-data     {...}}

      ;; called after the first failing trial
      {:type         :failure
       :fail         [...failing args...]
       :failing-size 13
       :num-tests    <tests ran before failure found>
       :pass?        false
       :property     #<...>
       :result       false/exception
       :result-data  {...}
       :seed         42}

    It will also be called on :complete, :shrink-step and :shrunk.

  Examples:

      (def p (for-all [a gen/pos-int] (> (* a a) a)))

      (quick-check 100 p)
      (quick-check 200 p
                   :seed 42
                   :max-size 50
                   :reporter-fn (fn [m]
                                  (when (= :failure (:type m))
                                    (println \"Uh oh...\"))))"
  [num-tests property & {:keys [seed max-size reporter-fn]
                         :or {max-size 200, reporter-fn (constantly nil)}}]
  (let [[created-seed rng] (make-rng seed)
        size-seq (gen/make-size-range-seq max-size)
        start-time (get-current-time-millis)]
    (loop [so-far 0
           size-seq size-seq
           rstate rng]
      (if (== so-far num-tests)
        (complete property num-tests created-seed start-time reporter-fn)
        (let [[size & rest-size-seq] size-seq
              [r1 r2] (random/split rstate)
              result-map-rose (gen/call-gen property r1 size)
              result-map (rose/root result-map-rose)
              result (:result result-map)
              args (:args result-map)
              so-far (inc so-far)]
          (if (results/pass? result)
            (do
              (reporter-fn {:type            :trial
                            :args            args
                            :num-tests       so-far
                            :num-tests-total num-tests
                            :pass?           true
                            :property        property
                            :result          result
                            :result-data     (results/result-data result)
                            :seed            seed})
              (recur so-far rest-size-seq r2))
            (failure property result-map-rose so-far size
                     created-seed start-time reporter-fn)))))))

(defn- smallest-shrink
  [total-nodes-visited depth smallest start-time]
  (let [{:keys [result]} smallest]
    {:total-nodes-visited total-nodes-visited
     :depth depth
     :pass? false
     :result (legacy-result result)
     :result-data (results/result-data result)
     :time-shrinking-ms (- (get-current-time-millis) start-time)
     :smallest (:args smallest)}))

(defn- shrink-loop
  "Shrinking a value produces a sequence of smaller values of the same type.
  Each of these values can then be shrunk. Think of this as a tree. We do a
  modified depth-first search of the tree:

  Do a non-exhaustive search for a deeper (than the root) failing example.
  Additional rules added to depth-first search:
  * If a node passes the property, you may continue searching at this depth,
  but not backtrack
  * If a node fails the property, search its children
  The value returned is the left-most failing example at the depth where a
  passing example was found.

  Calls reporter-fn on every shrink step."
  [rose-tree reporter-fn]
  (let [start-time (get-current-time-millis)
        shrinks-this-depth (rose/children rose-tree)]
    (loop [nodes shrinks-this-depth
           current-smallest (rose/root rose-tree)
           total-nodes-visited 0
           depth 0]
      (if (empty? nodes)
        (smallest-shrink total-nodes-visited depth current-smallest start-time)
        (let [;; can't destructure here because that could force
              ;; evaluation of (second nodes)
              head (first nodes)
              tail (rest nodes)
              result (:result (rose/root head))
              args (:args (rose/root head))
              pass? (results/pass? result)
              reporter-fn-arg {:type :shrink-step
                               :shrinking {:args                args
                                           :depth               depth
                                           :pass?               (boolean pass?)
                                           :result              result
                                           :result-data         (results/result-data result)
                                           :smallest            (:args current-smallest)
                                           :total-nodes-visited total-nodes-visited}}]
          (if pass?
            ;; this node passed the test, so now try testing its right-siblings
            (do
              (reporter-fn reporter-fn-arg)
              (recur tail current-smallest (inc total-nodes-visited) depth))
            ;; this node failed the test, so check if it has children,
            ;; if so, traverse down them. If not, save this as the best example
            ;; seen now and then look at the right-siblings
            ;; children
            (let [new-smallest (rose/root head)]
              (reporter-fn (assoc-in reporter-fn-arg
                                     [:shrinking :smallest]
                                     (:args new-smallest)))
              (if-let [children (seq (rose/children head))]
                (recur children new-smallest (inc total-nodes-visited) (inc depth))
                (recur tail new-smallest (inc total-nodes-visited) depth)))))))))

(defn- failure
  [property failing-rose-tree trial-number size seed start-time reporter-fn]
  (let [failed-after-ms (- (get-current-time-millis) start-time)
        root (rose/root failing-rose-tree)
        result (:result root)
        failure-data {:fail            (:args root)
                      :failing-size    size
                      :num-tests       trial-number
                      :pass?           false
                      :property        property
                      :result          (legacy-result result)
                      :result-data     (results/result-data result)
                      :failed-after-ms failed-after-ms
                      :seed            seed}]

    (reporter-fn (assoc failure-data :type :failure))

    (let [shrunk (shrink-loop failing-rose-tree
                              #(reporter-fn (merge failure-data %)))]
      (reporter-fn (assoc failure-data
                          :type :shrunk
                          :shrunk shrunk))
      (-> failure-data
          (dissoc :property)
          (assoc :shrunk shrunk)))))
