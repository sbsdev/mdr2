(ns mdr2.archive.async
  (:require
   [clojure.core.async :refer [<! close! go-loop]]
   [mdr2.archive.core :as archive]
   [mdr2.queues :as queues]
   [mount.core :refer [defstate]]))

(defstate archive-consumer
  :start (go-loop []
           (when-let [production (<! queues/archive)]
                   (archive/archive production)
                   (recur)))

  :stop (when archive-consumer
          (close! archive-consumer)))
