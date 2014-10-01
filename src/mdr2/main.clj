(ns mdr2.main
  (:require [immutant.web :as web]
            [immutant.messaging :as msg]
            [immutant.scheduling :as scheduling]
            [mdr2.queues :as queues]
            [mdr2.handler :as handler]
            [mdr2.abacus :as abacus]
            [mdr2.production :as production]
            [mdr2.archive :as archive]
            [mdr2.encode :as encode]))

(defn -main []
  ;; start web server
  (web/run-dmc handler/app)

  ;; wire up queues
  (msg/listen queues/create-queue #(production/create %))
  (msg/listen queues/encode-queue #(encode/encode %))
  (msg/listen queues/archive-queue #(archive/archive %))
  (msg/listen queues/notify-abacus-queue #(abacus/export-file %))
  (msg/listen queues/metadata-update #(production/update-or-create! %))

  ;; set up cron jobs
  ;; get new productions from ABACUS
  (scheduling/schedule abacus/import-new-productions :cron "0 */10 6-20 ? * MON-FRI")

  ;; get recorded productions from ABACUS
  (scheduling/schedule abacus/import-recorded-productions :cron "0 */10 6-20 ? * MON-FRI"))
