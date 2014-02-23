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
;; Since `with-redefs-fn` affects all threads, we must create an
;; implementation that can be shared across threads. The current idea is to
;; use thread-ids to key into a map stored in atom. This will allow each
;; thread to access its 'thread-state' (Semaphore and SynchronousQueue).
;; When a thread wants to do some concurrency work, it yields to the
;; scheduler. This is a process of sending a message to the scheduler
;; (over the SynchronousQueue), and then waiting on a Semaphore. Once
;; the thread has acquired the semaphore, it proceeds concurrently until
;; it reaches another yield. Depending on what concurrency function is making
;; the thread yield, it will send `nil`, or some other message to the
;; scheduler. `nil` is sent when the thread simply needs to wait its turn,
;; and doesn't need to alert the scheduler of any new or completed threads.
;; Some functions, like `future-call` introduce new threads. This message
;; to the scheduler is a chance for a thread to notify the scheduler of this
;; new thread. Since we don't actually know the thread-id until the thread
;; is running, we deliver a promise to the scheduler. The new (or pooled)
;; thread will deliver its thread-id to this promise. The scheduler will
;; block indefinitely waiting for the thread-id to be delivered.
;;
;; NOTE: maybe instead of actually using future-call, we can just
;; spawn threads ourselves. That way we can make note of the thread-id
;; before it actually starts.
(defn schedule
  ""
  [function]
  (let [state-atom-map (atom {})
        thread-id-promise (promise)]
    ))


;; ---------------------------------------------------------------------------
;; with-redef
;; ---------------------------------------------------------------------------

;; NOTE: I was hoping to be able to use closures in the redefined functions,
;; but I just realized that each time a new with-redef is used, it will
;; trample over the other one... Maybe I can use `binding`?

(defn get-state
  [state-atom-map
   (let [thread-id (.getId (Thread/currentThread))]
     (get @state-atom-map thread-id))])

(defn swap!-redef
  [state-atom-map]
  (let [old-swap! swap!]
    (fn [& args]
      (let [state (get-state state-atom-map)]
        (yield state nil)
        (apply old-swap! args)))))

(defn future-call-wrapped-fun
  [fun state-atom-map prom]
  (let [thread-id (.getId (Thread/currentThread))]
    (fn []
      (deliver prom thread-id))
))

(defn future-call-redef
  [state-atom-map]
  (let [old-future-call future-call]
    (fn [f]
      (let [state (get-state state-atom-map)
            prom (promise)
            wrapped-fun (future-call-wrapped-fun f state-atom-map prom)]
        (yield state [:future-call prom])
        (future-call wrapped-fun)))))


(defn concurrency-redef
  [state-atom-map func]
  (with-redefs-fn {#'swap! (swap!-redef state-atom-map)} func))
