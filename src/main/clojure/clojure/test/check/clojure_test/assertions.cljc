(ns clojure.test.check.clojure-test.assertions
  #?(:cljs (:require-macros [clojure.test.check.clojure-test.assertions.cljs]))
  (:require #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t])
            [clojure.test.check.results :as results]))

#?(:clj
   (defn test-context-stacktrace [st]
     (drop-while
       #(let [class-name (.getClassName ^StackTraceElement %)]
          (or (.startsWith class-name "java.lang")
              (.startsWith class-name "clojure.test$")
              (.startsWith class-name "clojure.test.check.clojure_test$")
              (.startsWith class-name "clojure.test.check.clojure_test.assertions")))
       st)))

#?(:clj
   (defn file-and-line*
     [stacktrace]
     (if (seq stacktrace)
       (let [^StackTraceElement s (first stacktrace)]
         {:file (.getFileName s) :line (.getLineNumber s)})
       {:file nil :line nil})))

(defn check-results [{:keys [result] :as m}]
  (if (results/passing? result)
    (t/do-report
      {:type :pass
       :message (dissoc m :result)})
    (t/do-report
      (merge {:type :fail
              :expected {:result true}
              :actual m}
             #?(:clj (file-and-line*
                       (test-context-stacktrace (.getStackTrace (Thread/currentThread))))
                :cljs (t/file-and-line (js/Error.) 4))))))

(defn check?
  [_ form]
  `(let [m# ~(nth form 1)]
     (check-results m#)))


#?(:clj
   (defmethod t/assert-expr 'clojure.test.check.clojure-test/check?
     [_ form]
     (check? _ form)))
