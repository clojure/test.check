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

(defn thread-communicator
  []
  {:semaphore (semaphore)
   :sync-queue (synchronous-queue)})

(defn yield
  [{sem :semaphore sync-queue :sync-queue} value]
  ;;(println "yield from: " (thread-id!))
  (put! sync-queue value)
  (acquire! sem)
  (release! sem))

(defn get-next-action
  [thread-state]
  (println "get next action: " (:id thread-state))
  (take! (-> thread-state :communicator :sync-queue)))

(defn update-action
  [thread-state]
  (assoc thread-state :next-action (get-next-action thread-state)))

(defn advance
  [thread-state]
  (println "advance: " (:id thread-state))
  (let [sem (-> thread-state :communicator :semaphore)]
    (release! sem)
    (acquire! sem)
    thread-state))

;; ---------------------------------------------------------------------------
;; with-redef
;; ---------------------------------------------------------------------------

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
      (function)
      (println "finishing thread: " (thread-id!))
      (yield state [:thread-completed (thread-id!)]))))

(defn future-call-redef
  [state-atom-map]
  (fn [f]
    (let [state (get-state state-atom-map)
          wrapped-fn (wrap-thread-fn f state-atom-map)
          new-thread (Thread. ^Thread wrapped-fn)
          new-thread-id (.getId new-thread)]
      (yield state [:thread-start new-thread]))))

(defn concurrency-redef
  [state-atom-map func]
  (with-redefs-fn {#'swap! (swap!-redef state-atom-map)
                   #'future-call (future-call-redef state-atom-map)}
                  func))


;; ---------------------------------------------------------------------------
;; scheduler
;; ---------------------------------------------------------------------------

(defn new-thread-state
  [thread-ident]
  {:id thread-ident
   :communicator (thread-communicator)
   :state :runnable
   :pending nil
   :next-action nil
   })

(defn runnable?
  [thread-state]
  (= (:state thread-state) :runnable))

(declare schedule-loop)

;; The scheduler will maintain a Semaphore and a SynchronousQueue with each
;; thread created. The SynchronousQueue is used for the thread to tell the
;; scheduler what non-deterministic action it wants to take. The Semaphore
;; is then used for the scheduler to tell the thread when it can run again.
;; Since `with-redefs-fn` affects all threads, we must create an
;; implementation that can be shared across threads. The current idea is to
;; use thread-ids to key into a map stored in atom. This will allow each
;; thread to access its 'thread-communicator' (Semaphore and SynchronousQueue).
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
;;
;;
;; Whenever a thread yields, it notifies the scheduler of its side-effect.
;; This serves several purposes:
;;
;;   1. Pause execution of the thread
;;   2. Alert the scheduler of some share-state change the thread would like
;;      to make.
;;   3. Alert the scheduler of something the thread would like to wait on.
;;
;; Many times, all three of these circumstances are true. For example, an
;; acquire on a Semaphore needs to alert the scheduler that the permits are
;; taken, and it needs to wait until the permits are available.
;;
;;
;; The scheduler maintains several bits of state. Some of this state is
;; internal, some shared with the scheduled threads themselves (via an atom).
;; Internally, the scheduler stores two things, primarily:
;;
;;   1. A shared-variable map. This is map stores shared-variable -> state.
;;      For example, the state for a given semaphore might share the number
;;      of available permits. For a deref'able object, it might store
;;      whether the value is ready, and what it is.
;;  2. A piece of state for each scheduled thread. This is in addition to the
;;     communication (Semaphore and SynchronousQueue) stored for each thread.
;;     For each thread, two things are stored:
;;
;;       1. runnable? A boolean of whether or not the action the thread
;;          wants to run is current runnable. ie., it's not blocked waiting
;;          for something else to occur.
;;
;;
;;  Concurrency primitives in Clojure:
;;
;;  atoms: atom swap! reset! compare-and-set!
;;  futures: future future-(call|done|cancel|cancelled) future?
;;  threads: (binding stuff not interesting now)
;;  misc: locking pcalls pvalues pmap seque promise deliver
;;  refs: ref deref sync dosync io!
;;  agents: agent agent-error send send-off restart-agent send-via await ...
;;  watches: add-watch remove-watch
(defn schedule
  ""
  [function]
  (let [state-atom-map (atom {})
        thread (Thread. ^Thread (wrap-thread-fn function state-atom-map))
        thread-ident (.getId thread)
        first-thread-state (new-thread-state thread-ident)]
    (swap! state-atom-map #(assoc % thread-ident (:communicator first-thread-state)))
    (concurrency-redef
      state-atom-map
      (fn []
        (.start thread)
        (schedule-loop state-atom-map
                       {thread-ident (update-action first-thread-state)}
                       [])))))

(defn schedule-loop
  [state-atom-map thread-states history]
  ;; this first implementation will simply run each thread to completion
  ;; before moving on to the next one. Its really unfair.
  (println "")
  (println "Schedule loop **************")
  (println "thread ids: " (keys thread-states))
  (println "history: " history)
  (if-not (empty? thread-states)
    (let [first-runnable (first (filter runnable? (vals thread-states)))]
      (println "operating on thread: " (:id first-runnable))
      (let [value (:next-action first-runnable)
            history-value [(:id first-runnable) value]]
        (println "the value is: " value)
        (cond
          (keyword? value)
          (let [new-thread-states (assoc thread-states
                                         (:id first-runnable)
                                         ;; NOTE: in some cases, is it too early
                                         ;; to 'advance' the thread here?
                                         (-> first-runnable advance update-action))]
            (recur state-atom-map new-thread-states (conj history history-value)))

          (= (first value) :thread-start)
          (let [thread-ident (.getId (second value))
                thread-state-1 (new-thread-state thread-ident)
                _ (core-swap! state-atom-map #(assoc % thread-ident (:communicator thread-state-1)))
                _ (.start (second value))
                thread-state (update-action thread-state-1)
                new-thread-states (assoc thread-states
                                         (:id first-runnable)
                                         ;; NOTE: in some cases, is it too early
                                         ;; to 'advance' the thread here?
                                         (-> first-runnable advance update-action))]
            (recur state-atom-map
                   (assoc new-thread-states thread-ident thread-state)
                   (conj history [(:id first-runnable) [:thread-start thread-ident]])))

          (= (first value) :thread-completed)
          (let [thread-ident (second value)]
            (println "removing thread: " thread-ident)
            (core-swap! state-atom-map #(dissoc % thread-ident))
            (recur state-atom-map (dissoc thread-states thread-ident) (conj history history-value))))))
    history))
