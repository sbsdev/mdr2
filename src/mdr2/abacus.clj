(ns mdr2.abacus
  (:require [clj-xpath.core :refer [xml->doc $x:text]])
  (:import javax.xml.XMLConstants
           org.xml.sax.SAXException
           javax.xml.validation.SchemaFactory
           javax.xml.transform.stream.StreamSource
           java.io.File))

(def root "/AbaConnectContainer/Task/Transaction/DocumentData/")
(def schema "resources/schema/abacus_export.xsd")

(def param-mapping
  {:product-number "artikel_nr"
   :title "MetaData/dc/title"
   :creator "MetaData/dc/creator"
   :date "MetaData/dc/date"
   :source "MetaData/dc/source"
   :language "MetaData/dc/language"
   ;; :source_edition "MetaData/sbs/auflageJahr"
   ;; :source_publisher "MetaData/sbs/verlag"
   })

(defn read-file [file]
  (let [xml (-> file slurp xml->doc)]
    (into {} (for [[key path] param-mapping] 
               [key ($x:text (str root path) xml)]))))

(defn valid? [file]
  ;; basically a minimal port of
  ;; http://stackoverflow.com/questions/15732/whats-the-best-way-to-validate-an-xml-file-against-an-xsd-file
  (let [validator (.newValidator
                   (.newSchema
                    (SchemaFactory/newInstance XMLConstants/W3C_XML_SCHEMA_NS_URI)
                    (StreamSource. (File. schema))))]
    (try
      (.validate validator (StreamSource. (File. file)))
      true
      (catch SAXException e false))))
