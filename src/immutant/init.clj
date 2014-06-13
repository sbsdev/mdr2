(ns immutant.init
  (:require [immutant.web :as web]
            [immutant.messaging :as msg]
            [immutant.jobs :as jobs]
            [mdr2.web :refer [app]]
            [mdr2.abacus :as abacus]))

(web/start app)

(msg/start "queue.create")
(msg/start "queue.encode")
(msg/start "queue.archive")

(jobs/schedule "abacus-import" abacus/import-file "0 */10 6-20 ? * MON-FRI")
