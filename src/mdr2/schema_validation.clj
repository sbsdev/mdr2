(ns mdr2.schema-validation
  "Schema validation for XML"
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log])
  (:import javax.xml.XMLConstants
           org.xml.sax.SAXException
           org.xml.sax.SAXParseException
           org.xml.sax.ErrorHandler
           javax.xml.validation.SchemaFactory
           javax.xml.transform.stream.StreamSource))

(defn extract-error
  "Return a map with the error mesage and the line number given an `exception`"
  [^SAXParseException exception]
  {:error (.getMessage exception)
   :line (.getLineNumber exception)
   :column (.getColumnNumber exception)})

(defn validation-errors
  "Return the first validation error when validating `file` against
  the given Relaxng `schema`. If the file is valid nil is returned."
  [file schema]
  ;; basically a minimal port of
  ;; http://stackoverflow.com/questions/15732/whats-the-best-way-to-validate-an-xml-file-against-an-xsd-file
  ;; also some inspiration came from
  ;; http://stackoverflow.com/questions/1541253/how-to-validate-an-xml-document-using-a-relax-ng-schema-and-jaxp
  (let [errors (atom [])
        error-handler (reify ErrorHandler
                        (fatalError [this exception]
                          (swap! errors conj (extract-error exception)))
                        (error [this exception]
                          (swap! errors conj (extract-error exception)))
                        (warning [this exception]))
        language XMLConstants/RELAXNG_NS_URI
        ;; We are using jing here because the plain validator (using
        ;; W3C_XML_SCHEMA_NS_URI) doesn't seem to work properly. It
        ;; claims that smil files are valid against the vubis bulk
        ;; import schema.
        factory "com.thaiopensource.relaxng.jaxp.XMLSyntaxSchemaFactory"
        schema-stream (io/input-stream (io/resource schema))
        validator (doto (.newValidator
                         (.newSchema
                          (SchemaFactory/newInstance language factory nil)
                          (StreamSource. schema-stream)))
                    (.setErrorHandler error-handler))]
    (try
      (.validate validator (StreamSource. file))
      @errors
      (catch SAXException e
        (log/error (.getMessage e))
        @errors)
      (finally (.close schema-stream)))))

(defn valid?
  "Check if a `file` is valid against given `schema`"
  [file schema]
  (empty? (validation-errors file schema)))
