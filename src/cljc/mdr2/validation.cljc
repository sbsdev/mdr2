(ns mdr2.validation
  #_(:require [struct.core :as st]))

;; FIXME: this should be merged with mdr2.production.spec

(defn library-signature?
  "Return true if `id` is a valid library signature"
  [id]
  (re-matches #"^ds\d{5,6}$" id))
