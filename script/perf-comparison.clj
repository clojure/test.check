(ns user.perf-comparison
  (:require [clojure.java.shell :as sh]
            [clojure.pprint :refer [pprint print-table]]
            [incanter.stats :as stats]))

(def monkeypatch
  (pr-str
   '(eval
     '(do
        (require '[clojure.test :as test])

        (def the-times (read-string (System/getenv "TCHECK_TEST_TIMES")))

        (alter-var-root (resolve 'test/run-tests)
                        (fn [orig]
                          (fn [& args]
                            (let [rets
                                  (repeatedly (inc the-times) ;; inc for warmup
                                              (fn []
                                                (let [b (System/currentTimeMillis)]
                                                  (assoc
                                                   (apply orig args)
                                                   :millis
                                                   (- (System/currentTimeMillis) b)))))]
                              (if-let [failure (first (filter #(pos? (+ (:fail %) (:error %))) rets))]
                                (throw (ex-info "Test failure!" failure)))
                              (spit "jvm-runtimes.edn" (->> rets
                                                            (rest)
                                                            (map :millis)
                                                            (pr-str)))
                              (first rets)))))))))

(defn measure-jvm
  "Returns a collection of runtimes in millis."
  [times]
  ;; TODO: get the monkeypatch script to check for failures
  (let [ret
        (sh/sh "lein"
               "do"
               "clean,"
               "update-in"
               ":injections"
               "conj"
               monkeypatch
               "--"
               "test"
               :env (assoc (into {} (System/getenv))
                           "TCHECK_TEST_TIMES" (str times)))]
    (when-not (zero? (:exit ret))
      (throw (ex-info "JVM tests failed!" ret))))
  (let [ret (read-string (slurp "jvm-runtimes.edn"))]
    (sh/sh "rm" "jvm-runtimes.edn")
    ret))

(defn measure-node-dev
  [times]
  (sh/sh "lein" "do" "clean," "cljsbuild" "once" "node-dev")
  (doall
   (repeatedly times
               (fn []
                 (let [b (System/currentTimeMillis)
                       {:keys [out] :as ret}
                       (sh/sh "node" "test-runners/run.js")]
                   (when-not (re-find #"(?m)\n0 failures, 0 errors.\n" out)
                     (throw (ex-info "node-dev tests failed!" ret)))
                   (- (System/currentTimeMillis) b))))))

(defn measure-node-adv
  [times]
  (sh/sh "lein" "do" "clean," "cljsbuild" "once" "node-adv")
  (doall
   (repeatedly times
               (fn []
                 (let [b (System/currentTimeMillis)
                       {:keys [out] :as ret}
                       (sh/sh "node" "target/cljs/node_adv/tests.js")]
                   (when-not (re-find #"(?m)\n0 failures, 0 errors.\n" out)
                     (throw (ex-info "node-adv tests failed!" ret)))
                   (- (System/currentTimeMillis) b))))))

(defmacro with-fixed-seeds
  [& body]
  ;; stupid way to get fixed seeds
  `(let [impl-file# "src/main/clojure/clojure/test/check/impl.cljc"]
     (spit impl-file#
           "(let [a (atom 42)] (defn get-current-time-millis [] (swap! a inc)))"
           :append true)
     (try
       ~@body
       (finally
         (sh/sh "git" "checkout" impl-file#)))))

(defn measure
  [git-ref times]
  (sh/sh "git" "checkout" git-ref)
  (try
    (with-fixed-seeds
      {:jvm      (measure-jvm times)
       :node-dev (measure-node-dev times)
       :node-adv (measure-node-adv times)})
    (finally
      (sh/sh "git" "checkout" "-"))))

(defn summarize-measurements
  [base candidate]
  (print-table [:thing :p-value :x-mean :y-mean :x-var :y-var :n1 :n2]
               (for [k (keys base)]
                 (assoc
                  (stats/t-test (get base k) :y (get candidate k))
                  :thing k))))

(defn compare-versions
  [base candidate sample-size]
  (with-fixed-seeds
    (let [n (read-string sample-size)]
      (doto [(measure base n) (measure candidate n)]
        (pprint)
        (->> (apply summarize-measurements))))))

(if (= 3 (count *command-line-args*))
  (apply compare-versions *command-line-args*)
  (binding [*out* *err*]
    (println "Usage: lein perf-comparison <git-ref-base> <git-ref-candidate> <sample-size>")
    (System/exit 1)))

(shutdown-agents)
