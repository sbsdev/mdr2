(ns mdr2.abacus
  (:require [clj-xpath.core :refer [xml->doc $x:text]]
            [clojure.java.io :refer [file delete-file]]
            [immutant.messaging :as msg])
  (:import javax.xml.XMLConstants
           org.xml.sax.SAXException
           javax.xml.validation.SchemaFactory
           javax.xml.transform.stream.StreamSource
           java.io.File))

(def ^:private root "/AbaConnectContainer/Task/Transaction/DocumentData/")
(def ^:private schema "resources/schema/abacus_export.xsd")
;;(def import-dir "/var/spool/mdr2")
(def ^:private import-dir "/home/eglic/tmp/mdr2")

(def ^:private param-mapping
  {:productNumber "artikel_nr"
   :title "MetaData/dc/title"
   :creator "MetaData/dc/creator"
   :date "MetaData/dc/date"
   :source "MetaData/dc/source"
   :language "MetaData/dc/language"
   ;; :source_edition "MetaData/sbs/auflageJahr"
   ;; :source_publisher "MetaData/sbs/verlag"
   })

(defn- read-file
  "Read an export file from ABACUS and return a map with all the data"
  [file]
  (let [xml (-> file slurp xml->doc)]
    (into {} (for [[key path] param-mapping]
               [key ($x:text (str root path) xml)]))))

(defn- valid?
  "Check if an export file from ABACUS is valid"
  [file]
  ;; basically a minimal port of
  ;; http://stackoverflow.com/questions/15732/whats-the-best-way-to-validate-an-xml-file-against-an-xsd-file
  (let [validator (.newValidator
                   (.newSchema
                    (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
                    (StreamSource. (File. schema))))]
    (try
      (.validate validator (StreamSource. file))
      true
      (catch SAXException e false))))

(defn import-file
  "Import new productions from ABACUS and put them on the create queue"
  []
  (doseq [f (filter #(.isFile %)
                    (file-seq (file import-dir)))]
    (when (valid? f)
      (msg/publish "queue.create" (read-file f))
      (delete-file f))))

(defn status-sync
  "Import status updates from ABACUS and put them on the archive queue"
  []
  (doseq [f (filter #(.isFile %)
                    (file-seq (file import-dir)))]
    (when (valid? f)
      (msg/publish "queue.archive" (read-file f))
      (delete-file f))))

(defn notify
  "Create an XML file to be read by ABACUS. This is used to send
  status notifications to ABACUS."
  [production]
  )
