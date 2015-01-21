(defproject org.clojure/test.check "0.6.2-SNAPSHOT"
  :description "A QuickCheck inspired property-based testing library."
  :url "https://github.com/clojure/test.check"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies []
  :source-paths ["src/main/clojure"]
  :test-paths ["src/test/clojure"]
  :profiles {:dev {:dependencies [[org.clojure/clojure "1.6.0"]
                                  ;; just for rng testing
                                  [org.apache.commons/commons-math3 "3.2"]]}}
  :global-vars {*warn-on-reflection* true}
  :plugins [[codox "0.8.10"]]
  :codox {:defaults {:doc/format :markdown}}
  :aliases {"prngs" ["run" "-m" "clojure.main" "-e" "(do (load-file \"prng_compare.clj\") (user/do-everything))"]})
