(ns mdr2.queues
  (:require [immutant.messaging :as msg]))

(defn create [] (msg/queue "create"))
(defn encode [] (msg/queue "encode"))
(defn encode-multi-volume [] (msg/queue "encode-multi-volume"))
(defn archive [] (msg/queue "archive"))
(defn notify-abacus [] (msg/queue "notify-abacus"))
(defn metadata-update [] (msg/queue "metadata-update"))

