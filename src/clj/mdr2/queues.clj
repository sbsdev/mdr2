(ns mdr2.queues
  (:require
   [clojure.core.async :refer [<! buffer chan close!]]
   [mount.core :refer [defstate]]))

(defstate encode
  "Queue to asynchronously handle events to encode a production"
  :start (chan (buffer 50))

  :stop (when encode (close! encode)))

(defstate archive
  "Queue to asynchronously handle events to archive a production"
  :start (chan (buffer 50))
  
  :stop (when archive (close! archive)))

(defstate repair
  "Queue to asynchronously handle events to repair a production"
  :start (chan (buffer 50))

  :stop (when repair (close! repair)))

(defstate notify-abacus
  "Queue to asynchronously handle events to notify ABACUS of state changes in a production"
  :start (chan (buffer 50))
  
  :stop (when notify-abacus (close! notify-abacus)))
