;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns ^{:author "Gary Fredericks"
      :doc "clojure.spec specs for test.check"}
    clojure.test.check.specs
  (:require [clojure.test.check :as t.c]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]
            [clojure.test.check.clojure-test :as clojure-test]
            #?(:clj [clojure.spec :as s]
               :cljs [cljs.spec :as s])))

(defmulti reporter-fn-return :type)
; (defmethod reporter-fn-return )

(s/def ::t.c/seed int?)
(s/def ::t.c/max-size nat-int?)
(s/def ::t.c/reporter-fn
  (s/fspec :args (s/cat :arg (s/keys))
           :ret any?))
(s/def ::t.c/result any?)
(s/def ::t.c/num-tests nat-int?)
(s/def ::t.c/failing-size nat-int?)
(s/def ::t.c/fail ::t.c/result)
(s/def ::t.c/total-nodes-visited nat-int?)
(s/def ::t.c/depth nat-int?)
(s/def ::t.c/smallest vector?)
(s/def ::t.c/shrunk (s/keys :req-un [::t.c/total-nodes-visited
                                     ::t.c/depth ::t.c/result
                                     ::t.c/smallest]))

(s/def ::quick-check-ret
  (s/keys :req-un [::t.c/result ::t.c/num-tests ::t.c/seed]
          :opt-un [::t.c/failing-size ::t.c/fail ::t.c/shrunk]))

;; worth doing better than this?
(def property? gen/generator?)
