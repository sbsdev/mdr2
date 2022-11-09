(ns mdr2.encode.async
  (:require
   [clojure.core.async :refer [<! close! go-loop]]
   [clojure.tools.logging :as log]
   [mdr2.encode.core :as encode]
   [mdr2.queues :as queues]
   [mount.core :refer [defstate]]))

(defstate encode-consumer
  :start (go-loop []
           (when-let [{:keys [production bitrate sample-rate]} (<! queues/encode)]
             (try
               (if (and bitrate sample-rate)
                 (encode/encode-or-split production bitrate sample-rate)
                 (encode/encode-or-split production))
               (catch Exception e
                 (log/errorf "Failed to encode %s because %s (bitrate: %s sample rate: %s)"
                             (:id production) (ex-message e) bitrate sample-rate)))
             (recur)))

  :stop (when encode-consumer
          (close! encode-consumer)))
