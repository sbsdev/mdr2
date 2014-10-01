(ns mdr2.queues
  (:require [immutant.messaging :as msg]))

;; set up queues
(def create-queue (msg/queue "create"))
(def encode-queue (msg/queue "encode"))
(def archive-queue (msg/queue "archive"))
(def notify-abacus-queue (msg/queue "notify-abacus"))
(def metadata-update (msg/queue "metadata-update"))

