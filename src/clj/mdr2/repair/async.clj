(ns mdr2.repair.async
  (:require
   [clojure.core.async :refer [<! close! go-loop]]
   [mdr2.repair.core :as archive]
   [mdr2.queues :as queues]
   [mount.core :refer [defstate]]
   [mdr2.repair.core :as repair]))

(defstate repair-consumer
  :start (go-loop []
           (when-let [production (<! queues/repair)]
                   (repair/repair production)
                   (recur)))

  :stop (when repair-consumer
          (close! repair-consumer)))
