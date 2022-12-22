(ns mdr2.queues
  (:require
   [clojure.core.async :refer [<! buffer chan close!]]
   [mount.core :refer [defstate]]))

(defstate encode
  "Queue to asynchronously handle events to encode a production"
  ;; encoding takes a while, so we need a decent buffer size
  :start (chan (buffer 100))

  :stop (when encode (close! encode)))

(defstate archive
  "Queue to asynchronously handle events to archive a production"
  ;; archiving takes a while and happens in bursts, so we need a
  ;; decent buffer size
  :start (chan (buffer 100))

  :stop (when archive (close! archive)))

(defstate repair
  "Queue to asynchronously handle events to repair a production"
  :start (chan (buffer 50))

  :stop (when repair (close! repair)))

(defstate notify-abacus
  "Queue to asynchronously handle events to notify ABACUS of state changes in a production"
  :start (chan (buffer 50))

  :stop (when notify-abacus (close! notify-abacus)))
