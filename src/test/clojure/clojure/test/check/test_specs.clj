(ns clojure.test.check.test-specs
  )

(if (let [{:keys [major minor]} *clojure-version*]
      (and (= 1 major) (< minor 9)))
  ;; don't bother testing this on older clojures
  (def valid-reporter-fn-call? (constantly true))

  (do
    (require '[clojure.spec.alpha :as s])
    (eval
     '(do

        (s/def ::base
          (s/keys :req-un [::type ::seed ::num-tests
                           ::property]))

        (defmulti type->spec :type)

        (defmethod type->spec :trial
          [_]
          (s/merge ::base
                   (s/keys :req-un [::args
                                    ::result
                                    ::result-data])))

        (defmethod type->spec :failure
          [_]
          (s/merge ::base
                   (s/keys :req-un [::fail
                                    ::failing-size
                                    ::result
                                    ::result-data])))

        (s/def ::shrunk
          (s/keys :req-un [::depth ::result ::result-data ::smallest ::total-nodes-visited]))

        (s/def ::shrinking
          (s/merge ::shrunk (s/keys :req-un [::args])))

        (defmethod type->spec :shrink-step
          [_]
          (s/merge ::base
                   (s/keys :req-un [::fail
                                    ::failing-size
                                    ::result
                                    ::result-data
                                    ::shrinking])))

        (defmethod type->spec :shrunk
          [_]
          (s/merge ::base
                   (s/keys :req-un [::fail
                                    ::failing-size
                                    ::result
                                    ::result-data
                                    ::shrunk])))

        (defmethod type->spec :complete
          [_]
          (s/merge ::base
                   (s/keys :req-un [::result])))

        (s/def ::value (s/multi-spec type->spec :type))

        (defn valid-reporter-fn-call?
          [m]
          (or
           (s/valid? ::value m)
           (s/explain ::value m)))))))
