(ns mdr2.repair.async
  (:require
   [clojure.core.async :refer [<! close! go-loop]]
   [clojure.tools.logging :as log]
   [mdr2.i18n :refer [tr]]
   [mdr2.mail :as mail]
   [mdr2.queues :as queues]
   [mdr2.repair.core :as repair]
   [mount.core :refer [defstate]]))

(defstate repair-consumer
  :start (go-loop []
           (when-let [production (<! queues/repair)]
             (try
               ;; drop the :repair/initiated-by as it is just used to
               ;; convey the user that initiated the repair
               (repair/repair (dissoc production :repair/initiated-by))
               (when-let [email (get-in production [:repair/initiated-by :mail])]
                 (let [{:keys [id title]} production
                       subject (tr [:repair-subject] [id])
                       body (tr [:repair-message] [id title])]
                   (mail/send-message email subject body)))
               (catch Exception e
                 (log/errorf "Failed to repair %s because %s" production (ex-message e))))
             (recur)))

  :stop (when repair-consumer
          (close! repair-consumer)))
