(ns immutant.init
  (:require [immutant.web :as web]
            [immutant.messaging :as msg]
            [immutant.jobs :as jobs]
            [mdr2.handler :as handler]
            [mdr2.abacus :as abacus]
            [mdr2.pipeline1 :as pipeline1]
            [mdr2.db :as db]
            [mdr2.archive :as archive]))

(web/start handler/app)

;; set up queues...
(msg/start "queue.create")
(msg/start "queue.archive")
(msg/start "queue.notify-abacus")

;; ...and wire them up
(msg/listen "queue.create" #(db/add-production %))
(msg/listen "queue.archive" #(archive/archive %))
(msg/listen "queue.notify-abacus" #(abacus/notify %))

;; get new productions from ABACUS
(jobs/schedule "abacus-import" abacus/import-file "0 */10 6-20 ? * MON-FRI")

;; get status updates on existing productions from ABACUS
(jobs/schedule "abacus-status-sync" abacus/status-sync "0 */10 6-20 ? * MON-FRI")
