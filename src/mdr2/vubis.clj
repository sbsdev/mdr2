(ns mdr2.vubis
  "Import and validate XML files from the library system"
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml-> xml1-> attr= text]]
            [mdr2.production :as production]))

(def ^:private param-mapping
  {:title [:datafield (attr= :tag "245") :subfield (attr= :code "a")]
   :creator [:datafield (attr= :tag "100") :subfield]
   :source [:datafield (attr= :tag "020") :subfield]
   :description [:datafield (attr= :tag "520") :subfield]
   :library_number [:datafield (attr= :tag "091") :subfield (attr= :code "a")]
   :source_publisher [:datafield (attr= :tag "534") :subfield (attr= :code "c")]
   :source_date [:datafield (attr= :tag "534") :subfield (attr= :code "d")]})

(defn validate
  "Returns an empty seq on successful validation of `file` as a proper
  vubis export file or a seq of error messages otherwise"
  [file]
  ;; FIXME: implement proper validation for vubis export files
  ())

(defn get-subfield
  "Get the subfield text for the given `path` in the given `record`.
  Returns nil if there is no such subfield"
  [record path]
  (some-> (apply xml1-> record path) text))

(defn read-file
  "Read an export file from VUBIS and return a map with all the data"
  [file]
  (let [root (-> file io/file xml/parse zip/xml-zip)]
    (for [record (xml-> root :record)]
      (into {} (for [[key path] param-mapping
                     :let [val (get-subfield record path)]
                     :when (some? val)]
                 [key val])))))

