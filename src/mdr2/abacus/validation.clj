(ns mdr2.abacus.validation
  "Validate XML files from ABACUS"
  (:require [clojure.java.io :as io]
            [mdr2.schema-validation :refer [valid?]])
  (:import javax.xml.XMLConstants
           org.xml.sax.SAXException
           javax.xml.validation.SchemaFactory
           javax.xml.transform.stream.StreamSource))

(def ^:private open-schema "schema/open_production.rng")
(def ^:private recorded-schema "schema/recorded_production.rng")
(def ^:private metadata-schema "schema/metadata_sync.rng")
(def ^:private status-request-schema "schema/status_request.rng")

(defn valid-open?
  "Check if an export file from ABACUS is a valid request for opening
  a production"
  [file]
  (valid? file open-schema))

(defn valid-recorded?
  "Check if an export file from ABACUS is valid request for announcing
  that a production is recorded"
  [file]
  (valid? file recorded-schema))

(defn valid-metadata-sync?
  "Check if an export file from ABACUS is valid request for
  synchronizing meta data"
  [file]
  (valid? file metadata-schema))

(defn valid-status-request?
  "Check if an export file from ABACUS is valid request status
  information"
  [file]
  (valid? file status-request-schema))

