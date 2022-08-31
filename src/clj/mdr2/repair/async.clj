(ns mdr2.repair.async
  (:require
   [clojure.core.async :refer [<! close! go-loop]]
   [mdr2.repair.core :as archive]
   [mdr2.queues :as queues]
   [mount.core :refer [defstate]]
   [mdr2.repair.core :as repair]
   [failjure.core :as fail]
   [clojure.tools.logging :as log]))

(defstate repair-consumer
  :start (go-loop []
           (when-let [production (<! queues/repair)]
             (fail/when-let-failed? [cause (repair/repair production)]
               (log/errorf "Failed to repair %s because %s" production cause))
             (recur)))

  :stop (when repair-consumer
          (close! repair-consumer)))
