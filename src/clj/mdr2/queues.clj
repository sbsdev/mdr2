(ns mdr2.queues
  (:require
   [clojure.core.async :refer [<! buffer chan close!]]
   [mount.core :refer [defstate]]))

(defstate encode
  :start (chan (buffer 30))

  :stop (when encode (close! encode)))

(defstate archive
  :start (chan (buffer 30))
  
  :stop (when archive (close! archive)))

(defstate repair
  :start (chan (buffer 30))

  :stop (when repair (close! repair)))

(defstate notify-abacus
  :start (chan (buffer 30))
  
  :stop (when notify-abacus (close! notify-abacus)))
