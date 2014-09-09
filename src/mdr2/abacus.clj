(ns mdr2.abacus
  (:require [clj-xpath.core :refer [xml->doc $x:text]]
            [clojure.java.io :refer [file delete-file]]
            [clojure.string :as string]
            [immutant.messaging :as msg]
            [environ.core :refer [env]]
            [mdr2.production :as production])
  (:import javax.xml.XMLConstants
           org.xml.sax.SAXException
           javax.xml.validation.SchemaFactory
           javax.xml.transform.stream.StreamSource
           java.io.File))

(def ^:private root "/AbaConnectContainer/Task/Transaction/DocumentData/")
(def ^:private schema "resources/schema/abacus_export.xsd")
(def ^:private import-dir (env :abacus-import-dir))
(def ^:private export-dir (env :abacus-export-dir))

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
  (doseq [f (filter #(and (.isFile %) (valid? %))
                    (file-seq (file import-dir)))]
    (msg/publish "queue.create" (read-file f))
    (delete-file f)))

(defn status-sync
  "Import status updates from ABACUS and put them on the archive queue"
  []
  (doseq [f (filter #(and (.isFile %) (valid? %))
                    (file-seq (file import-dir)))]
    (let [{product-number :productNumber} (read-file f)
          production (production/find-by-productnumber product-number)]
      (msg/publish "queue.archive" production)
      (delete-file f))))

(defn notify
  "Create an XML file to be read by ABACUS. This is used to send
  status notifications to ABACUS."
  [production])

(defn escape
  "Escape a string `s` for ABACUS consumption"
  [s]
  (if (or (char? s) (string? s))
    (str "'" s "'") ; only escape strings
    s))

(defn wrap-rows
  "Wrap an export record according to ABACUS conventions"
  [rs]
  (concat [(list "N" "ADO")] ; 'N' for new
          rs
          [(list "S")])) ; 's' for save

(defn create-row
  "Create a row for export according to ABACUS conventions"
  [k v]
  (when v
    (let [section (if (= k 4) "IBF" "ADO")]
      (list "D" section k (escape v)))))

(defn export
  "Export the state of a production as a csv-like structure, ready to
  be consumed by ABACUS"
  [{:keys [productNumber totalTime state audioFormat multimediaType
           date id producedDate volumes] :as production}]
  (->> [(create-row 2 productNumber)
        (create-row 233 totalTime) ; in minutes
        (create-row 106 state) ; Process Status Madras
        ;; FIXME: what exactly is the document status? In what way is
        ;; it different than the process status? In my view there is
        ;; only one state and that is the state of the production. Is
        ;; the production ready for recording, is it waiting for
        ;; volume splitting, etc?
        (create-row 107 :FIXME) ; Document Status Madras
        (create-row 276 audioFormat)
        ;; FIXME: the number of CDs is only determined at the time of
        ;; archiving. Is this persisted in the db?
        (create-row 275 volumes ; Number of CDs
        (create-row 277 multimediaType)
        ;; FIXME: again the date of the end of the production is only
        ;; known once we start the archiving. Should this be persisted
        ;; to the db?
        (create-row 252 producedDate) ; Date of production end
        (create-row 274 date) ; Date of production start
        (create-row 4 (production/dam-number production))] ; for legacy purposes
       (remove nil?) ; remove empty rows
       wrap-rows ; wrap the payload
       (map-indexed #(conj %2 %1)) ; number each row
       (map #(string/join "," %))
       (map println-str)
       (string/join)))

(defn export-file
  "Export the state of a production into a special file in
  `export-dir`. The file is named with the `productNumber`. Existing
  files are overwritten"
  [{productNumber :productNumber :as production}]
  ;; file names are supposed to be "Ax_productNumber.txt, e.g. Ax_DY15000.txt"
  (let [file-name (.getPath (file export-dir "Ax_" productNumber ".txt"))]
    (spit file-name (export production))))
