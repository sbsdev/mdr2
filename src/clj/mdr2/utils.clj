(ns mdr2.utils
  (:require [clojure.tools.logging :as log]
            [failjure.core :as fail]))

(defn log-and-fail
  "Log an error with given `msg` and return a (Failjure) failure"
  [msg]
  (log/error msg)
  (fail/fail msg))

