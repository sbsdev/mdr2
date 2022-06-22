(ns mdr2.vubis
  "Import and validate XML files from the library system"
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml-> xml1-> attr= text]]
            [mdr2.schema-validation :as validation]
            [mdr2.production :as prod]))

(def ^:private param-mapping
  {:title [:datafield (attr= :tag "245") :subfield (attr= :code "a")]
   :creator [:datafield (attr= :tag "100") :subfield]
   :source [:datafield (attr= :tag "020") :subfield]
   :description [:datafield (attr= :tag "520") :subfield]
   :library_number [:datafield (attr= :tag "091") :subfield (attr= :code "a")]
   :source_publisher [:datafield (attr= :tag "534") :subfield (attr= :code "c")]
   :source_date [:datafield (attr= :tag "534") :subfield (attr= :code "d")]
   :language [:datafield (attr= :tag "041") :subfield (attr= :code "a")]})

(def ^:private schema "schema/vubis_export.rng")

(def iso-639-2-to-iso-639-1
  "Mapping between three letter codes of [ISO
  639-2](http://en.wikipedia.org/wiki/ISO_639-2) and the two letter
  codes of [ISO 639-1](http://en.wikipedia.org/wiki/ISO_639-1)"
  {"ger" "de"
   "gsw" "de-CH"
   "fre" "fr"
   "eng" "en"})

(def default-language
  "Default language to use if the language provided in the vubis
  export file is not known in `iso-639-2-to-iso-639-1`"
  "de")

(defn validate
  "Returns an empty list on successful validation of `file` as a
  proper vubis export file or a list of error messages otherwise"
  [file]
  (->>
   (validation/validation-errors file schema)
   (map validation/stringify)))

(defn get-subfield
  "Get the subfield text for the given `path` in the given `record`.
  Returns nil if there is no such subfield"
  [record path]
  (some-> (apply xml1-> record path) text))

(defn clean-raw-production
  "Return a proper production based on a raw production, i.e.
  translate the language tag into proper ISO 639-1 codes"
  [production]
  (assoc production :language
         (iso-639-2-to-iso-639-1 (:language production) default-language)))

(defn read-file
  "Read an export file from VUBIS and return a map with all the data"
  [file]
  (let [root (-> file io/file xml/parse zip/xml-zip)]
    (for [record (xml-> root :record)]
      (->> (for [[key path] param-mapping
                 :let [val (get-subfield record path)]
                 :when (some? val)]
             [key val])
           (into {})
           clean-raw-production
           prod/parse))))
