(ns jdbc-ring-session.cleaner
  (:require [clojure.java.jdbc :as jdbc]))

(defn remove-sessions [conn]
  (let [t (quot (System/currentTimeMillis) 1000)]
    (jdbc/delete! conn :session_store ["idle_timeout < ? or absolute_timeout < ?" t t])))

(defprotocol Stoppable
  "Something that can be stopped"
  (stopped? [_] "Return true if stopped, false otherwise")
  (stop     [_] "Stop (idempotent)"))

(defn sleep [millis]
  (let [timeout (+ millis (System/currentTimeMillis))]
    (while (< (System/currentTimeMillis) timeout)
      (try (Thread/sleep (- timeout (System/currentTimeMillis)))
        (catch InterruptedException _
          (.interrupt ^Thread (Thread/currentThread)))))))

(defn start-cleaner
  ([db]
    (start-cleaner db {}))
  ([db {:keys [interval-secs]
        :or {interval-secs 60}}]
    (let [state  (atom :running)
          interval-ms (* 1000 interval-secs)]
      (-> (fn runner []
            (while @state
              (remove-sessions db)
              (sleep interval-ms)))
          (Thread.)
          (.start))
      (reify Stoppable
        (stopped? [_] (not @state))
        (stop     [_] (swap! state (constantly false)))))))

(defn stop-cleaner [session-cleaner]
  {:pre [(satisfies? Stoppable session-cleaner)]}
  (.stop session-cleaner))
