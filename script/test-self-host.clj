(require '[cljs.build.api]
         '[clojure.java.io :as io])

(cljs.build.api/build "src/target/cljs/self-host"
  {:main                  'clojure.test.check.test.runner
   :output-to             "target/out-self-host/main.js"
   :output-dir            "target/out-self-host"
   :target                :nodejs
   :cache-analysis-format :edn})

(defn copy-source
  [filename]
  (spit (str "target/out-self-host/" filename)
    (slurp (io/resource filename))))

(copy-source "cljs/test.cljc")
(copy-source "cljs/analyzer/api.cljc")
(copy-source "cljs/reader.clj")
(copy-source "clojure/template.clj")
