(defproject reiddraper/simple-check "0.5.0-SNAPSHOT"
  :description "A QuickCheck inspired property-based testing library."
  :url "http://github.com/reiddraper/simple-check"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.0"]
                 [clj-tuple "0.1.2"]
                 [codox-md "0.2.0" :exclusions [org.clojure/clojure]]]
  :global-vars {*warn-on-reflection* true}
  :codox {:writer codox-md.writer/write-docs}
  :plugins [[codox "0.6.4"]])
