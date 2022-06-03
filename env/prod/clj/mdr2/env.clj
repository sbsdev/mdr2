(ns mdr2.env
  (:require [clojure.tools.logging :as log]))

(def defaults
  {:init
   (fn []
     (log/info "\n-=[mdr2 started successfully]=-"))
   :stop
   (fn []
     (log/info "\n-=[mdr2 has shut down successfully]=-"))
   :middleware identity})
