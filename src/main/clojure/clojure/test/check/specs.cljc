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
(s/def ::prop/property property?)

(s/fdef t.c/quick-check
        :args (s/cat :num-tests number?
                     :property ::prop/property
                     :opts (s/* any?) #_ (s/keys* #_#_:opt-un [#_#_#_::t.c/seed
                                             ::t.c/max-size
                                             ::t.c/reporter-fn]))
        :ret ::quick-check-ret)

(s/fdef prop/for-all*
        :args (s/cat :args (s/coll-of gen/generator?)
                     :function ifn?)
        :ret property?)

(s/fdef prop/for-all
        :args (s/cat :bindings :clojure.core.specs/bindings
                     :body (s/* any?)))

(s/fdef clojure-test/defspec
        :args (s/cat :name simple-symbol?
                     :opts (s/? any?)
                     :property any?))

;;
;; Generators
;;

(s/def ::gen/fmap-fn (s/fspec :args (s/cat :arg any?) :ret any?))
(def ^:private POS_INFINITY #?(:clj Double/POSITIVE_INFINITY, :cljs (.-POSITIVE_INFINITY js/Number)))
(s/def ::gen/size (s/and number? #(<= 0 %) #(< % POS_INFINITY)))

(s/fdef gen/fmap
        :args (s/cat :f ifn? #_::gen/fmap-fn ;; TODO: why can't I?
                     :gen gen/generator?)
        :ret gen/generator?)
(s/fdef gen/bind
        :args (s/cat :gen gen/generator?
                     :f ifn?)
        :ret gen/generator?)
(s/fdef gen/sample
        :args (s/cat :gen gen/generator?
                     :num-samples (s/? nat-int?))
        :ret coll?)
(s/fdef gen/generate
        :args (s/cat :gen gen/generator?
                     :size (s/? ::gen/size))
        :ret any?)
(s/fdef gen/sized
        :args (s/cat :sized-gen ifn?
                     ;; TODO: why can't I?
                     #_(s/fspec :args (s/cat :size ::gen/size)
                                         :ret gen/generator?))
        :ret gen/generator?)
(s/fdef gen/resize
        :args (s/cat :n ::gen/size
                     :generator gen/generator?)
        :ret gen/generator?)
(s/fdef gen/scale
        :args (s/cat :f (s/fspec :args (s/cat :size ::gen/size)
                                 :ret ::gen/size)
                     :gen gen/generator?)
        :ret gen/generator?)
(s/fdef gen/choose
        :args (s/cat :lower int?
                     :upper int?)
        :ret gen/generator?)
(s/fdef gen/one-of
        :args (s/cat :gens (s/spec (s/cat :gens (s/+ gen/generator?))))
        :ret gen/generator?)
(s/fdef gen/frequency
        :args (s/cat :pairs (s/coll-of
                             (s/tuple pos-int? gen/generator?)))
        :ret gen/generator?)
(s/fdef gen/elements
        :args (s/cat :coll (s/spec
                            (s/cat :elements (s/+ any?))))
        :ret gen/generator?)
(s/def ::gen/max-tries pos-int?)
(s/def ::gen/pred
  (s/with-gen ifn?
    #(gen/return (constantly true))))
(s/def ::gen/gen
  (s/with-gen gen/generator?
    #(gen/return (gen/return 42))))
(defn error?
  [x]
  (instance? #?(:clj Throwable :cljs js/Error) x))
(s/def ::gen/ex-fn
  (s/fspec :args (s/cat :arg (s/keys :req-un [::gen/max-tries
                                              ::gen/pred
                                              ::gen/gen]))
           :ret error?))
(s/fdef gen/such-that
        :args (s/cat :pred ifn?
                     :gen gen/generator?
                     :max-tries-or-opts (s/?
                                         (s/alt :max-tries nat-int?
                                                :opts (s/keys :opt-un
                                                              [::gen/max-tries
                                                               ::gen/ex-fn]))))
        :ret gen/generator?)

(s/fdef gen/not-empty
        :args (s/cat :gen gen/generator?)
        :ret gen/generator?)

(s/fdef gen/no-shrink
        :args (s/cat :gen gen/generator?)
        :ret gen/generator?)

(s/fdef gen/shrink-2
        :args (s/cat :gen gen/generator?)
        :ret gen/generator?)

(s/fdef gen/tuple
        :args (s/cat :gens (s/* gen/generator?))
        :ret gen/generator?)

(s/fdef gen/vector
        :args (s/cat :gen gen/generator?
                     :size-opts (s/? (s/alt :num-elements nat-int?
                                            ;; TODO: use s/and somehow to say max >= min?
                                            :min-and-max (s/cat :min-elements nat-int?
                                                                :max-elements nat-int?))))
        :ret gen/generator?)

(s/fdef gen/list
        :args (s/cat :gen gen/generator?)
        :ret gen/generator?)

(s/fdef gen/shuffle
        :args (s/cat :coll coll?)
        :ret gen/generator?)

(s/fdef gen/hash-map
        :args (s/cat :kvs (s/* (s/cat :k keyword? :gen gen/generator?)))
        :ret gen/generator?)

(s/def ::gen/num-elements nat-int?)
(s/def ::gen/min-elements nat-int?)
(s/def ::gen/max-elements nat-int?)
(s/def ::gen/distinct-coll-gen-opts
  (s/keys :opt-un [::gen/num-elements ::gen/min-elements ::gen/max-elements ::gen/max-tries ::gen/ex-fn]))

(s/fdef gen/vector-distinct
        :args (s/cat :gen gen/generator?
                     :opts (s/? ::gen/distinct-coll-gen-opts))
        :ret gen/generator?)
(s/fdef gen/list-distinct
        :args (s/cat :gen gen/generator?
                     :opts (s/? ::gen/distinct-coll-gen-opts))
        :ret gen/generator?)
(s/fdef gen/vector-distinct-by
        :args (s/cat :key-fn ifn?
                     :gen gen/generator?
                     :opts (s/? ::gen/distinct-coll-gen-opts))
        :ret gen/generator?)
(s/fdef gen/list-distinct-by
        :args (s/cat :key-fn ifn?
                     :gen gen/generator?
                     :opts (s/? ::gen/distinct-coll-gen-opts))
        :ret gen/generator?)
(s/fdef gen/set
        :args (s/cat :gen gen/generator?
                     :opts (s/? ::gen/distinct-coll-gen-opts))
        :ret gen/generator?)
(s/fdef gen/sorted-set
        :args (s/cat :gen gen/generator?
                     :opts (s/? ::gen/distinct-coll-gen-opts))
        :ret gen/generator?)
(s/fdef gen/map
        :args (s/cat :key-gen gen/generator?
                     :val-gen gen/generator?
                     :opts (s/? ::gen/distinct-coll-gen-opts))
        :ret gen/generator?)

(s/def :clojure.test.check.generators.large-integer*/max int?)
(s/def :clojure.test.check.generators.large-integer*/min int?)

(s/fdef gen/large-integer*
        :args (s/cat :opts (s/keys :opt-un [:clojure.test.check.generators.large-integer*/max
                                            :clojure.test.check.generators.large-integer*/min]))
        :ret gen/generator?)

(s/def :clojure.test.check.generators.double*/max double?)
(s/def :clojure.test.check.generators.double*/min double?)
(s/def ::gen/infinite? boolean?)
(s/def ::gen/NaN? boolean?)

(s/fdef gen/double*
        :args (s/cat :opts (s/keys :opt-un [:clojure.test.check.generators.double*/max
                                            :clojure.test.check.generators.double*/min
                                            ::gen/infinite?
                                            ::gen/NaN?]))
        :ret gen/generator?)

(s/fdef gen/recursive-gen
        :args (s/cat :container-gen-fn ifn?
                     :scalar-gen gen/generator?)
        :ret gen/generator?)

(s/fdef gen/let
        :args (s/cat :bindings (s/or :vector-bindings
                                     :clojure.core.specs/bindings

                                     :map-bindings
                                     (s/map-of :clojure.core.specs/binding-form any?))
                     :body (s/* any?)))
