(ns mdr2.encode.async
  (:require
   [clojure.core.async :refer [<! close! go-loop]]
   [mdr2.encode.core :as encode]
   [mdr2.queues :as queues]
   [mount.core :refer [defstate]]))

(defstate encode-consumer
  :start (go-loop []
           (when-let [{:keys [production bitrate sample-rate]} (<! queues/encode)]
             (if (and bitrate sample-rate)
               (encode/encode-or-split production bitrate sample-rate)
               (encode/encode-or-split production))))

  :stop (when encode-consumer
          (close! encode-consumer)))
