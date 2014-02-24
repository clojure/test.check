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


;; ---------------------------------------------------------------------------
;; concurrency-helpers
;; ---------------------------------------------------------------------------

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

(defn thread-id!
  "Return the id of the calling thread."
  []
  (.getId (Thread/currentThread)))

;; ---------------------------------------------------------------------------
;; thread/scheduler communication
;; ---------------------------------------------------------------------------

(defn thread-state
  []
  {:semaphore (semaphore)
   :sync-queue (synchronous-queue)})

(defn yield
  [{sem :semaphore sync-queue :sync-queue} value]
  ;; (println "the sync-queue is: " sync-queue)
  (put! sync-queue value)
  (acquire! sem)
  (release! sem))

(defn advance
  [{sem :semaphore sync-queue :sync-queue}]
  (let [value (take! sync-queue)]
    (release! sem)
    (acquire! sem)
    value))


;; ---------------------------------------------------------------------------
;; with-redef
;; ---------------------------------------------------------------------------

;; NOTE: I was hoping to be able to use closures in the redefined functions,
;; but I just realized that each time a new with-redef is used, it will
;; trample over the other one... Maybe I can use `binding`?

(def core-swap! swap!)

(defn get-state
  ([state-atom-map]
   (let [thread-ident (thread-id!)]
     (get-state state-atom-map thread-ident)))
  ([state-atom-map thread-ident]
     (get @state-atom-map thread-ident)))

(defn swap!-redef
  [state-atom-map]
  (let [old-swap! swap!]
    (fn [& args]
      (let [state (get-state state-atom-map)]
        ;; (println "yielding with state: " state)
        (yield state :swap!)
        (apply old-swap! args)))))

(defn wrap-thread-fn
  [function state-atom-map]
  ;; By the time we've gotten here, the scheduler
  ;; already knows about us. But we do need to let
  ;; the scheduler know when we exit
  (fn []
    (let [state (get-state state-atom-map)]
      ;; (println "my thread is: " (thread-id!))
      ;; (println "the state is: " state)
      (yield state :thread-start)
      (function)
      (yield state [:thread-completed (thread-id!)]))))

(defn future-call-redef
  [state-atom-map]
  (fn [f]
    (let [state (get-state state-atom-map)
          wrapped-fn (wrap-thread-fn f state-atom-map)
          new-thread (Thread. ^Thread wrapped-fn)
          new-thread-id (.getId new-thread)]
      (swap! state-atom-map #(assoc % new-thread-id (thread-state)))
      (yield state [:thread-start new-thread-id])
      (.start new-thread)
      (yield state :thread-started))))

(defn concurrency-redef
  [state-atom-map func]
  (with-redefs-fn {#'swap! (swap!-redef state-atom-map)
                   #'future-call (future-call-redef state-atom-map)}
                  func))

;; ---------------------------------------------------------------------------
;; scheduler
;; ---------------------------------------------------------------------------

(declare schedule-loop)

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
        first-thread-state (thread-state)
        thread (Thread. ^Thread (wrap-thread-fn function state-atom-map))
        thread-ident (.getId thread)]
    (swap! state-atom-map #(assoc % thread-ident first-thread-state))
    ;; (println "The state-atom-map is: " @state-atom-map)
    (concurrency-redef
      state-atom-map
      (fn []
        (.start thread)
        (schedule-loop state-atom-map [thread-ident] [])))))

(defn schedule-loop
  [state-atom-map thread-ids history]
  ;; this first implementation will simply run each thread to completion
  ;; before moving on to the next one. Its really unfair.
  (println "thread ids: " thread-ids)
  (if-not (empty? thread-ids)
    (let [first-id (first thread-ids)
          state (get-state state-atom-map first-id)]
      (let [value (advance state)]
        ;; (println "the value is: " value)
        (cond
          (keyword? value)
          (recur state-atom-map thread-ids (conj history value))

          (= (first value) :thread-start)
          (recur state-atom-map (conj thread-ids (second value)) (conj history value))

          (= (first value) :thread-completed)
          (recur state-atom-map (vec (remove #(= (second value) %) thread-ids)) (conj history value))
          )))
    history))
