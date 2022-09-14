(ns mdr2.repair.async
  (:require
   [clojure.core.async :refer [<! close! go-loop]]
   [clojure.tools.logging :as log]
   [mdr2.queues :as queues]
   [mdr2.repair.core :as repair]
   [mount.core :refer [defstate]]))

(defstate repair-consumer
  :start (go-loop []
           (when-let [production (<! queues/repair)]
             (try
               (repair/repair production)
               (catch Exception e
                 (log/errorf "Failed to repair %s because %s" production (ex-message e))))
             (recur)))

  :stop (when repair-consumer
          (close! repair-consumer)))
