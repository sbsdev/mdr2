(ns mdr2.archive.async
  (:require
   [clojure.core.async :refer [<! close! go-loop]]
   [clojure.tools.logging :as log]
   [failjure.core :as fail]
   [mdr2.archive.core :as archive]
   [mdr2.queues :as queues]
   [mount.core :refer [defstate]]))

(defstate archive-consumer
  :start (go-loop []
           (when-let [production (<! queues/archive)]
             (when (fail/failed? (archive/archive production))
               (log/errorf "Failed to archive %s" production))
             (recur)))

  :stop (when archive-consumer
          (close! archive-consumer)))
