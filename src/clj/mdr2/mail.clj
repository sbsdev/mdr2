(ns mdr2.mail
  (:require
   [clojure.string :as string]
   [mdr2.config :refer [env]]
   [tarayo.core :as tarayo]))

(defn send-message
  "Send an email to `recipient` with the given `subject` and `message`.
  If `prefix` is defined and not blank then prepend the `subject` with
  it."
  [recipient subject message]
  (let [prefix (env :mail-prefix)
        prefixed-subject (if (string/blank? prefix) subject (format "%s %s" prefix subject))]
    (with-open [conn (tarayo/connect {:host (env :mail-host)})]
      (tarayo/send! conn {:from (env :mail-sender)
                          :to recipient
                          :subject prefixed-subject
                          :body message}))))

