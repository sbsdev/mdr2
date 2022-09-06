(ns mdr2.encode.async
  (:require
   [clojure.core.async :refer [<! close! go-loop]]
   [clojure.tools.logging :as log]
   [failjure.core :as fail]
   [mdr2.encode.core :as encode]
   [mdr2.queues :as queues]
   [mount.core :refer [defstate]]))

(defstate encode-consumer
  :start (go-loop []
           (when-let [{:keys [production bitrate sample-rate]} (<! queues/encode)]
             (if (and bitrate sample-rate)
               (fail/when-let-failed? [cause (encode/encode-or-split production bitrate sample-rate)]
                 (log/errorf "Failed to encode %s with bitrate %s and sample rate %s because %s" production bitrate sample-rate cause))
               (fail/when-let-failed? [cause (encode/encode-or-split production)]
                 (log/errorf "Failed to encode %s because %s" production cause)))
             (recur)))

  :stop (when encode-consumer
          (close! encode-consumer)))
