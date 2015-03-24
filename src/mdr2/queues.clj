(ns mdr2.queues
  (:require [immutant.messaging :as msg]))

(defn encode [] (msg/queue "encode"))
(defn archive [] (msg/queue "archive"))
(defn notify-abacus [] (msg/queue "notify-abacus"))

