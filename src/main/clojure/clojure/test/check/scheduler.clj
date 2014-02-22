;   Copyright (c) Rich Hickey, Reid Draper, and contributors.
;   All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;   which can be found in the file epl-v10.html at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.

(ns clojure.test.check.scheduler
  (:import [java.util.concurrent Semaphore SynchronousQueue]))

(defn semaphore
  ([]
   (semaphore 1))
  ([permits]
   (Semaphore. permits))
  ([permits fair]
   (Semaphore. permits fair)))

(defn acquire!
  [sem]
  (.acquire ^Semaphore sem))

(defn release!
  [sem]
  (.release ^Semaphore sem))

(defn synchronous-queue
  []
  (SynchronousQueue.))

(defn put!
  [queue object]
  (.put ^SynchronousQueue queue object))

(defn take!
  [queue]
  (.take ^SynchronousQueue queue))

(defn thread-state
  []
  {:semaphore (semaphore)
   :sync-queue (synchronous-queue)})

(defn yield
  [{sem :semaphore sync-queue :sync-queue} value]
  (put! sync-queue value)
  (acquire! sem)
  (release! sem))

(defn advance
  [{sem :semaphore sync-queue :sync-queue}]
  (let [value (take! sync-queue)]
    (release! sem)
    (acquire! sem)
    value))

;; The scheduler will maintain a Semaphore and a SynchronousQueue with each
;; thread created. The SynchronousQueue is used for the thread to tell the
;; scheduler what non-deterministic action it wants to take. The Semaphore
;; is then used for the scheduler to tell the thread when it can run again.
(defn schedule
  ""
  [function]
  true)


;; ---------------------------------------------------------------------------
;; with-redef
;; ---------------------------------------------------------------------------

;; NOTE: I was hoping to be able to use closures in the redefined functions,
;; but I just realized that each time a new with-redef is used, it will
;; trample over the other one... Maybe I can use `binding`?

(defn swap!-redef
  [state]
  (let [old-swap! swap!]
    (fn [& args]
      (println "swap! replacement called")
      (yield state "yield!")
      (apply old-swap! args))))

(defn concurrency-redef
  [state func]
  (with-redefs-fn {#'swap! (swap!-redef state)} func))
