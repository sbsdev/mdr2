(ns immutant.init
  (:require [immutant.web :as web]
            [immutant.messaging :as msg]
            [immutant.scheduling :as scheduling]
            [mdr2.handler :as handler]
            [mdr2.abacus :as abacus]
            [mdr2.pipeline1 :as pipeline1]
            [mdr2.production :as production]
            [mdr2.archive :as archive]))

(web/run-dmc handler/app)

;; set up queues...
(def create-queue (msg/queue "create"))
(def archive-queue (msg/queue "archive"))
(def notify-abacus-queue (msg/queue "notify-abacus"))

;; ...and wire them up
(msg/listen create-queue #(production/create %))
(msg/listen archive-queue #(archive/archive %))
(msg/listen notify-abacus-queue #(abacus/notify %))

;; get new productions from ABACUS
(scheduling/schedule abacus/import-file :cron "0 */10 6-20 ? * MON-FRI")

;; get status updates on existing productions from ABACUS
(scheduling/schedule abacus/status-sync :cron "0 */10 6-20 ? * MON-FRI")
