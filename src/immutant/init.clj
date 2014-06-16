(ns immutant.init
  (:require [immutant.web :as web]
            [immutant.messaging :as msg]
            [immutant.jobs :as jobs]
            [mdr2.web :refer [app]]
            [mdr2.abacus :as abacus]
            [mdr2.pipeline1 :as pipeline1]
            [mdr2.db :as db]))

(web/start app)

;; set up queues...
(msg/start "queue.create")
(msg/start "queue.encode")
(msg/start "queue.archive")

;; ...and wire them up
(msg/listen "queue.create" #(db/add-production %))
(msg/listen "queue.encode" #(msg/publish "queue.archive" (pipeline1/audio-encoder %)))

(jobs/schedule "abacus-import" abacus/import-file "0 */10 6-20 ? * MON-FRI")
