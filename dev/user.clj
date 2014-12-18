(ns user
  (:require [robert.hooke :as rh]
            [clojure.tools.logging :as log]
            [mdr2.db :as db]))

(defn debug [f & args]
  (log/debug "Calling " (:name (meta f)) " with " args)
  (apply f args))

(defn debug-ns [sym]
  (doseq [v (vals (ns-publics (find-ns sym)))]
    (rh/add-hook v #'debug))
  )

(comment
  (debug-ns 'mdr2.db))
