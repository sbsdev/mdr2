(ns immutant.init
  (:require [immutant.web :as web]
            [immutant.messaging :as msg]
            [mdr2.web :refer [app]]))

(web/start app)

(msg/start "queue.create")
(msg/start "queue.encode")
(msg/start "queue.archive")


