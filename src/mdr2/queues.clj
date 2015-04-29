(ns mdr2.queues
  "Define the required queues"
  (:require [immutant.messaging :as msg]))

;; FIXME: this seems like a bit of overkill. Wouldn't it be better to
;; define the queues in wildfly?
(defn encode [] (msg/queue "encode"))
(defn archive [] (msg/queue "archive"))
(defn notify-abacus [] (msg/queue "notify-abacus"))

