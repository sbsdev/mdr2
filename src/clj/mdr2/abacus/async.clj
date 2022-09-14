(ns mdr2.abacus.async
  (:require
   [clojure.core.async :refer [<! close! go-loop]]
   [clojure.tools.logging :as log]
   [mdr2.abacus.core :as abacus]
   [mdr2.queues :as queues]
   [mount.core :refer [defstate]]))

(defstate notify-abacus-consumer
  :start (go-loop []
           (when-let [production (<! queues/notify-abacus)]
             (try
               (abacus/export-file production)
               (catch Exception e
                 (log/errorf "Failed to notify ABACUS about %s because %s"
                             production (ex-message e))))
             (recur)))

  :stop (when notify-abacus-consumer
          (close! notify-abacus-consumer)))
