(ns mdr2.data.xml
  "Enhance the default data.xml emit functions so that the doctype declaration can be specified"
  (:require [clojure.data.xml :refer [check-stream-encoding flatten-elements emit-event]]))

(defn emit
  "Prints the given Element tree as XML text to stream.
   Options:
    :encoding <str>          Character encoding to use
    :doctype  <str>          Document type (DOCTYPE) declaration to use"
  [e ^java.io.Writer stream & {:keys [encoding doctype] :or {encoding "UTF-8"}}]
  (let [^javax.xml.stream.XMLStreamWriter writer (-> (javax.xml.stream.XMLOutputFactory/newInstance)
                                                     (.createXMLStreamWriter stream))]

    (when (instance? java.io.OutputStreamWriter stream)
      (check-stream-encoding stream encoding))

    (.writeStartDocument writer encoding "1.0")
    (when doctype
      (.writeDTD writer doctype))
    (doseq [event (flatten-elements [e])]
      (emit-event event writer))
    (.writeEndDocument writer)
    stream))

(defn emit-str
  "Emits the Element to String and returns it.
   Options:
    :encoding <str>          Character encoding to use in the XML Declaration
    :doctype  <str>          Document type (DOCTYPE) declaration to use"
  [e & {:keys [encoding doctype] :as opts}]
  (let [^java.io.StringWriter sw (java.io.StringWriter.)]
    (apply emit e sw (mapcat seq opts))
    (.toString sw)))

