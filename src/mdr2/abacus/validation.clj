(ns mdr2.abacus.validation
  "Validate XML files from ABACUS"
  (:require [clojure.java.io :as io]
            [mdr2.schema-validation :refer [validation-errors]])
  (:import javax.xml.XMLConstants
           org.xml.sax.SAXException
           javax.xml.validation.SchemaFactory
           javax.xml.transform.stream.StreamSource))

(def ^:private open-schema "schema/open_production.rng")
(def ^:private recorded-schema "schema/recorded_production.rng")
(def ^:private metadata-schema "schema/metadata_sync.rng")
(def ^:private status-request-schema "schema/status_request.rng")

(defn open-validation-errors
  "Check if an export `file` from ABACUS is a valid request for
  opening a production. Return nil if the file is valid or a
  validation error otherwise"
  [file]
  (validation-errors file open-schema))

(defn recorded-validation-errors
  "Check if an export `file` from ABACUS is a valid request for
  announcing that a production is recorded. Return nil if the file is
  valid or a validation error otherwise"
  [file]
  (validation-errors file recorded-schema))

(defn status-request-errors
  "Check if an export `file` from ABACUS is a valid request for status
  information. Return nil if the file is valid or a validation error
  otherwise"
  [file]
  (validation-errors file status-request-schema))

(defn metadata-sync-errors
  "Check if an export `file` from ABACUS is a valid request for
  synchronizing meta data. Return nil if the file is valid or a
  validation error otherwise"
  [file]
  (validation-errors file metadata-schema))
