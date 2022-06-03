(ns mdr2.abacus.async
  (:require
   [clojure.core.async :refer [<! buffer close! go-loop]]
   [mount.core :refer [defstate]]
   [mdr2.queues :as queues]
   [mdr2.abacus.core :as abacus]))

(defstate notify-abacus-consumer
  :start (go-loop []
           (when-let [production (<! queues/notify-abacus)]
             (abacus/export-file production)
             (recur)))

  :stop (when notify-abacus-consumer
          (close! notify-abacus-consumer)))
