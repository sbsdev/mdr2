(ns mdr2.main
  (:require [immutant.web :as web]
            [immutant.messaging :as msg]
            [immutant.scheduling :as scheduling]
            [mdr2.handler :as handler]
            [mdr2.abacus :as abacus]
            [mdr2.production :as production]
            [mdr2.archive :as archive]))

;; set up queues
(def create-queue (msg/queue "create"))
(def archive-queue (msg/queue "archive"))
(def notify-abacus-queue (msg/queue "notify-abacus"))

(defn -main []
  ;; start web server
  (web/run-dmc handler/app)

  ;; wire up queues
  (msg/listen create-queue #(production/create %))
  (msg/listen archive-queue #(archive/archive %))
  (msg/listen notify-abacus-queue #(abacus/export-file %))

  ;; set up cron jobs
  ;; get new productions from ABACUS
  (scheduling/schedule abacus/import-new-productions :cron "0 */10 6-20 ? * MON-FRI")

  ;; get recorded productions from ABACUS
  (scheduling/schedule abacus/import-recorded-productions :cron "0 */10 6-20 ? * MON-FRI"))
