(ns mdr2.abacus.validation
  (:require [clojure.java.io :as io])
  (:import javax.xml.XMLConstants
           org.xml.sax.SAXException
           javax.xml.validation.SchemaFactory
           javax.xml.transform.stream.StreamSource))

(def ^:private schema "schema/open_production.xsd")

(defn valid?
  "Check if an export file from ABACUS is valid"
  [file]
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

