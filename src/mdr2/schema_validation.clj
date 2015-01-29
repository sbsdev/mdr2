(ns mdr2.schema-validation
  "Schema validation for XML"
  (:require [clojure.java.io :as io])
  (:import javax.xml.XMLConstants
           org.xml.sax.SAXException
           javax.xml.validation.SchemaFactory
           javax.xml.transform.stream.StreamSource))

(defn validation-errors
  "Return the validation errors when validating `file` against the
  given Relaxng `schema`. If the file is valid an empty seq is
  returned."
  [file schema]
  ;; basically a minimal port of
  ;; http://stackoverflow.com/questions/15732/whats-the-best-way-to-validate-an-xml-file-against-an-xsd-file
  ;; also some inspiration came from
  ;; http://stackoverflow.com/questions/1541253/how-to-validate-an-xml-document-using-a-relax-ng-schema-and-jaxp
  (let [language XMLConstants/RELAXNG_NS_URI
        ;; We are using jing here because the plain validator (using
        ;; W3C_XML_SCHEMA_NS_URI) doesn't seem to work properly. It
        ;; claims that smil files are valid against the vubis bulk
        ;; import schema.
        factory "com.thaiopensource.relaxng.jaxp.XMLSyntaxSchemaFactory"
        schema-stream (io/input-stream (io/resource schema))
        validator (.newValidator
                   (.newSchema
                    (SchemaFactory/newInstance language factory nil)
                    (StreamSource. schema-stream)))]
    (try
      (.validate validator (StreamSource. file))
      []
      (catch SAXException e
        [(.getMessage e)])
      (finally (.close schema-stream)))))

(defn valid?
  "Check if a `file` is valid against given `schema`"
  [file schema]
  (empty? (validation-errors file schema)))
