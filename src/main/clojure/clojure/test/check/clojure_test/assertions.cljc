(ns clojure.test.check.clojure-test.assertions
  #?(:cljs (:require-macros [clojure.test.check.clojure-test.assertions.cljs]))
  (:require [clojure.string :as str]
            #?(:clj [clojure.test :as t]
               :cljs [cljs.test :as t])
            [clojure.test.check.results :as results]))

#?(:clj
   (defn test-context-stacktrace [st]
     (drop-while
       #(let [class-name (.getClassName ^StackTraceElement %)]
          (or (clojure.string/starts-with? class-name "java.lang")
              (clojure.string/starts-with? class-name "clojure.test$")
              (clojure.string/starts-with? class-name "clojure.test.check.clojure_test$")
              (clojure.string/starts-with? class-name "clojure.test.check.clojure_test.assertions")))
       st)))

#?(:clj
   (defn file-and-line*
     [stacktrace]
     (if (seq stacktrace)
       (let [^StackTraceElement s (first stacktrace)]
         {:file (.getFileName s) :line (.getLineNumber s)})
       {:file nil :line nil})))

#?(:cljs
   (defn file-and-line**
     [stack-string]

     (if-let [[_ file line] (->> stack-string
                                 (re-seq #"\bat\b[^\(\n]* \(([^\)]+):(\d+):(\d+)\)")
                                 (second))]
       {:file file :line line}
       {:file nil :line nil})))

(defn file-and-line
  #?(:clj
     "Only meant to be called from ClojureScript. In Clojure, does
     nothing and returns nil."
     :cljs
     "Returns a delay of a :file and :line map representing the file
  and line of the caller.")
  []
  #?(:cljs (let [e (js/Error.)] (delay (file-and-line** (.-stack e))))))

(defn check-results
  [{:keys [result] :as m} file-and-line]
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
                :cljs (deref file-and-line))))))

(defn check?
  [_ form]
  `(check-results ~@(rest form)))


#?(:clj
   (defmethod t/assert-expr 'clojure.test.check.clojure-test/check?
     [_ form]
     (check? _ form)))
