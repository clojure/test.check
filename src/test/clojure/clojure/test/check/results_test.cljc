;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.results-test
  (:require #?(:cljs
               [cljs.test :as test :refer-macros [are deftest testing is]])
            #?(:clj
               [clojure.test :refer :all])
            [clojure.test.check.results :as results]))

(deftest default-passing-values
  (is (not (results/pass? nil)))
  (is (not (results/pass? false)))
  (are [x] (results/pass? x)
    :keyword
    'symbol
    "string"
    []
    {}
    #{}
    ()
    42
    42.0
    true))
