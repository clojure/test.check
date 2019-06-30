(defproject org.clojure/test.check "0.10.0-SNAPSHOT"
  :description "A QuickCheck inspired property-based testing library."
  :url "https://github.com/clojure/test.check"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies []
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :jvm-opts ^:replace ["-Xmx512m" "-server"]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.9.0"]
                                  [org.clojure/clojurescript "1.10.520"]]}
             :self-host {:dependencies [[org.clojure/clojure "1.8.0"]
                                        [org.clojure/clojurescript "1.9.854"]]
                         :main clojure.main
                         :global-vars {*warn-on-reflection* false}}}
  :global-vars {*warn-on-reflection* true}
  :plugins [[lein-codox "0.10.7"]
            [lein-cljsbuild "1.1.5"]]
  ;; To generate codox files for a release:
  ;; 1) checkout the tagged git commit
  ;; 2) tweak the project.clj version to match, since
  ;;    jenkins only updates the pom.xml
  ;; 3) tweak the :source-uri entry below, replacing "master"
  ;;    with the appropriate tag
  ;; 4) run `lein codox`
  ;; 5) copy target/doc into the gh-pages branch source tree
  ;; 6) optionally also make sure the appropriate version-specific
  ;;    subdirectories are populated, and linked to from
  ;;    doc/api-docs-for-older-versions.md
  :codox {:namespaces [clojure.test.check
                       clojure.test.check.clojure-test
                       clojure.test.check.generators
                       clojure.test.check.properties
                       clojure.test.check.results]
          :source-uri {#".*" "https://github.com/clojure/test.check/blob/master/{filepath}#L{line}"}
          :doc-files ["doc/api-docs-for-older-versions.md"
                      "doc/cheatsheet.md"
                      "doc/generator-examples.md"
                      "doc/growth-and-shrinking.md"
                      "doc/intro.md"]}
  :cljsbuild
  {:builds
   [{:id "node-dev"
     :source-paths ["src/main/clojure" "src/test/clojure"
                    "src/target/cljs/node"]
     :notify-command ["node" "test-runners/run.js"]
     :compiler {:optimizations :none
                :main clojure.test.check.test.runner
                :static-fns true
                :target :nodejs
                :output-to "target/cljs/node_dev/tests.js"
                :output-dir "target/cljs/node_dev/out"
                :source-map true}}
    {:id "browser-dev"
     :source-paths ["src/main/clojure" "src/test/clojure"
                    "src/target/cljs/browser"]
     :compiler {:optimizations :none
                :static-fns true
                :output-to "target/cljs/browser_dev/tests.js"
                :output-dir "target/cljs/browser_dev/out"
                :source-map true}}
    {:id "node-adv"
     :source-paths ["src/main/clojure" "src/test/clojure"
                    "src/target/cljs/node"]
     :notify-command ["node" "target/cljs/node_adv/tests.js"]
     :compiler {:optimizations :advanced
                :main clojure.test.check.test.runner
                :target :nodejs
                :pretty-print false
                :output-to "target/cljs/node_adv/tests.js"
                :output-dir "target/cljs/node_adv/out"}}
    {:id "browser-adv"
     :source-paths ["src/main/clojure" "src/test/clojure"
                    "src/target/cljs/browser"]
     :compiler {:optimizations :advanced
                :pretty-print false
                :output-to "target/cljs/browser_adv/tests.js"
                :output-dir "target/cljs/browser_adv/out"}}]})
