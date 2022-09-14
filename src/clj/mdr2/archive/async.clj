(ns mdr2.archive.async
  (:require
   [clojure.core.async :refer [<! close! go-loop]]
   [clojure.tools.logging :as log]
   [mdr2.archive.core :as archive]
   [mdr2.queues :as queues]
   [mount.core :refer [defstate]]))

(defstate archive-consumer
  :start (go-loop []
           (when-let [production (<! queues/archive)]
             (try
               (archive/archive production)
               (catch Exception e
                 (log/errorf "Failed to archive %s because %s"
                             production (ex-message e))))
             (recur)))

  :stop (when archive-consumer
          (close! archive-consumer)))
