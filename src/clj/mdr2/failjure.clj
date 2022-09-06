(ns mdr2.failjure
  (:require [failjure.core :as fail]))

(defrecord AnnotatedFailure [message data]
  fail/HasFailed
  (failed? [self] true)
  (message [self] (:message self)))
