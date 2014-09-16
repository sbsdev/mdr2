(ns mdr2.abacus.validation
  "Validate XML files from ABACUS"
  (:require [clojure.java.io :as io])
  (:import javax.xml.XMLConstants
           org.xml.sax.SAXException
           javax.xml.validation.SchemaFactory
           javax.xml.transform.stream.StreamSource))

(def ^:private open-schema "schema/open_production.xsd")
(def ^:private recorded-schema "schema/recorded_production.xsd")
(def ^:private metadata-schema "schema/metadata_sync.xsd")
(def ^:private status-request-schema "schema/status_request.xsd")

(defn valid?
  "Check if a `file` is valid against given `schema`"
  [file schema]
  ;; basically a minimal port of
  ;; http://stackoverflow.com/questions/15732/whats-the-best-way-to-validate-an-xml-file-against-an-xsd-file
  (let [validator (.newValidator
                   (.newSchema
                    (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
                    (StreamSource. (io/file (io/resource schema)))))]
    (try
      (.validate validator (StreamSource. file))
      true
      (catch SAXException e false))))

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

