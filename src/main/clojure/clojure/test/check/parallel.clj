;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.parallel
  "Some beginning infrastructure for testing a property in parallel."
  (:import
    [java.util.concurrent
     BlockingQueue
     ConcurrentHashMap
     Executor
     Executors
     LinkedBlockingQueue
     ThreadFactory
     ThreadPoolExecutor
     TimeUnit]
    [java.util.concurrent.locks
     ReentrantLock
     Lock]))

(defn ^ThreadFactory thread-factory
  "Returns a ThreadFactory which names threads by calling `(name-generator)`."
  ([name-generator]
   (reify ThreadFactory
     (newThread [_ runnable]
       (let [name (name-generator)]
         (doto
           (Thread. nil #(.run ^Runnable runnable) name)
           (.setDaemon true)))))))

(defonce pool-capacity 100000)

(defonce ^Executor pool
;  "The executor we schedule our various tasks on."
  (let [cnt   (atom 0)
        procs (.. Runtime getRuntime availableProcessors)]
    (ThreadPoolExecutor.
      procs                                       ; Core pool size
      procs                                       ; Max pool size
      5 TimeUnit/SECONDS                          ; Keepalive time
      (LinkedBlockingQueue. ^int pool-capacity)   ; Queue
      (thread-factory #(str "test.check-" (swap! cnt inc))))))

(defn flatten1
  "Flatten a sequence of sequences. flatten1 only removes one layer of nesting,
  unlike clojure.core/flatten, which removes all layers of sequential values.
  Unlike (apply concat ...), flatten1 is lazy."
  [coll]
  (let [helper
        (fn helper [xs ys]
          (lazy-seq
            (if-let [s (seq ys)]
              (cons (first ys) (helper xs (rest ys)))
              (when-let [s (seq xs)]
                (helper (rest xs) (first xs))))))]
    (helper coll nil)))

(defn worker
  "Returns a function that calls `(f)` delivering results, or Throwable errors,
  to promise `p`."
  [f p]
  (fn wrapper []
    (try
      (deliver p (f))
      (catch Throwable t
        (deliver p t)))))

(defn run!
  "Runs a Runnable on the executor pool."
  [f]
  (.execute pool ^Runnable f))

(defn execute
  "Given a sequence of functions, `fs`, return a lazy sequence of the results
  of `fs`, evaluated with maximum parallelism of `parallelism`."
  [parallelism fs]
  ; We generate a lazy sequence of promises in 1:1 correspondance with fs.
  ;
  ; The result seq can be obtained by derefing each promise in turn.
  ;
  ; To *fulfill* those promises, we need to enqueue worker fns onto the pool
  ; which execute fs--but we can't enqueue them all at once. There might be an
  ; infinite number of tasks. We want to be speculatively executing fs
  ; `parallelism` elements ahead, so results are ready by the time we ask for
  ; them.
  ;
  ; So, as a side effect, when you consume an element from the result seq, we
  ; *also* enqueue a worker to run the function `f` elements ahead in the
  ; sequence.
  ;
  ; The first set of tasks we fire off immediately.
  (assert (< 0 parallelism))
  (let [commitments      (repeatedly promise)
        workers          (map worker fs commitments)
        [eager deferred] (split-at parallelism workers)]

    ; Execute the first `parallelism` tasks immediately.
    (->> eager (map run!) dorun)

    (map (fn [commitment worker]
           ; Block until this value is ready
           (let [value @commitment]

             ; Schedule the execution of a later fn.
             (when worker (run! worker))

             ; Then return the value itself.
             value))
         commitments
         ; Ensure that we have a worker or nil for every commitment.
         (concat deferred (repeat parallelism nil)))))

#_(defn execute
  "Given a sequence of functions, `fs`, return a lazy sequence of the results
  of `fs`, evaluated with maximum parallelism of `parallelism`."
  [parallelism fs]
  (let [[sparks potential] (split-at parallelism fs)
        futures (doall (map future-call sparks))
        step (fn step [futured xs]
               (lazy-seq
                 (if-let [s (seq xs)]
                   (cons (deref (first futured))
                         (step (-> futured
                                 next
                                 (concat [(future-call (first s))]))
                               (rest s)))
                   (map deref futured))))]
    (step futures potential)))
