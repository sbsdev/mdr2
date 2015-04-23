(ns mdr2.main
  (:require [immutant.web :as web]
            [immutant.messaging :as msg]
            [mdr2.queues :as queues]
            [mdr2.handler :as handler]
            [mdr2.abacus :as abacus]
            [mdr2.production :as prod]
            [mdr2.archive :as archive]
            [mdr2.encode :as encode])
  (:gen-class))

(defn -main []
  ;; start web server
  (web/run handler/site)
  (web/run handler/rest-api :path "/abacus")

  ;; wire up queues
  (msg/listen (queues/encode)
              (fn [{:keys [production bitrate sample-rate]}]
                (let [p (prod/iso8601ify production)]
                  (if (and bitrate sample-rate)
                    (encode/encode-or-split p bitrate sample-rate)
                    (encode/encode-or-split p)))))
  (msg/listen (queues/archive) #(archive/archive (prod/iso8601ify %)))
  (msg/listen (queues/notify-abacus) #(abacus/export-file (prod/iso8601ify %))))
