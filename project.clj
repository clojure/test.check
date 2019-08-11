(defproject org.clojure/test.check "0.11.0-SNAPSHOT"
  :description "A QuickCheck inspired property-based testing library."
  :url "https://github.com/clojure/test.check"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies []
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :jvm-opts ^:replace ["-Xmx512m" "-server"]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.10.1"]
                                  [org.clojure/clojurescript "1.10.520"]]}
             :self-host {:dependencies [[org.clojure/clojure "1.10.1"]
                                        [org.clojure/clojurescript "1.10.520"]]
                         :main clojure.main
                         :global-vars {*warn-on-reflection* false}}}
  :global-vars {*warn-on-reflection* true}
  :plugins [[lein-codox "0.10.7"]
            [lein-cljsbuild "1.1.5"]]
  ;; To generate codox files (which are hosted on the gh-pages branch)
  ;; for a release:
  ;;
  ;; 1) if the current content should be preserved as documentation
  ;;    for an old release, first copy it to a subdirectory and
  ;;    add a link in doc/api-docs-for-older-versions.md (on the
  ;;    master branch, I guess, though that won't help when building
  ;;    from an older tag, so you may also manually add that change
  ;;    to the api-docs-for-older-versions.html file on the gh-pages
  ;;    branch as well, which is no fun)
  ;; 2) checkout the tagged git commit
  ;; 3) tweak the project.clj version to match, since
  ;;    jenkins only updates the pom.xml
  ;; 4) tweak the :source-uri entry below, replacing "master"
  ;;    with the appropriate tag
  ;; 5) run `lein codox`
  ;; 6) copy target/doc into the gh-pages branch source tree, commit
  ;;    and push; e.g., if you have the gh-pages branch open on a
  ;;    worktree at gitignored/gh-pages, then this might work:
  ;;        D1=target/doc
  ;;        D2=gitignored/gh-pages
  ;;        for file in $(ls $D1); do
  ;;          if [[ -e $D2/$file ]]; then
  ;;            rm -rf $D2/$file
  ;;          fi
  ;;          cp -r {$D1,$D2}/$file
  ;;        done
  ;; 7) check the result at http://clojure.github.io/test.check/ ;
  ;;    the source links should go to the correct tag on github
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
