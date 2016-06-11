(ns clojure.test.check.specs
  (:require [clojure.test.check :as t.c]
            [clojure.test.check.generators :as gen]
            [clojure.spec :as s]))

(defmulti reporter-fn-return :type)
; (defmethod reporter-fn-return )

(s/def ::t.c/seed long?)
(s/def ::t.c/max-size nat-long?)
(s/def ::t.c/reporter-fn
  (s/fspec :args (s/cat :arg ::s/any)
           :ret ::s/any))
(s/def ::t.c/result ::s/any)
(s/def ::t.c/num-tests nat-long?)
(s/def ::t.c/failing-size nat-long?)
(s/def ::t.c/fail ::t.c/result)
(s/def ::t.c/total-nodes-visited nat-long?)
(s/def ::t.c/depth nat-long?)
(s/def ::t.c/smallest vector?)
(s/def ::t.c/shrunk (s/keys :req-un [::t.c/total-nodes-visited
                                     ::t.c/depth ::t.c/result
                                     ::t.c/smallest]))

(s/def ::quick-check-ret
  (s/keys :req-un [::t.c/result ::t.c/num-tests ::t.c/seed]
          :opt-un [::t.c/failing-size ::t.c/fail ::t.c/shrunk]))

;; worth doing better than this?
(def property? gen/generator?)

(s/fdef t.c/quick-check
        :args (s/cat :num-tests number?
                     :property property?
                     :opts (s/keys* :opt-un [::t.c/seed
                                             ::t.c/max-size
                                             ::t.c/reporter-fn]))
        :ret ::quick-check-ret)
