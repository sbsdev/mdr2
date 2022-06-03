(ns mdr2.validation
  #_(:require [struct.core :as st]))

(defn library-signature?
  "Return true if `id` is a valid library signature"
  [id]
  (re-matches #"^ds\d{5,}$" id))
