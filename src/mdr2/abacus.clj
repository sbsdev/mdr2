(ns mdr2.abacus
  "Import and export files to and from ABACUS

  Unfortunately the interface to our ERP is very crude: you basically
  read and write files from an directory. The import from ABACUS is
  done via XML and the export to ABACUS is done via some form of csv.
  The interface imports the following notifications:

  - Start (open) a production
  - a production is recorded
  - Meta data updates

  and the interface sends notifications to ABACUS when:

  - the state of a production changes
  - meta data of a production changes."
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml1-> attr= attr text]]
            [clojure.string :as string]
            [immutant.messaging :as msg]
            [environ.core :refer [env]]
            [mdr2.queues :as queues]
            [mdr2.production :as production]
            [mdr2.abacus.validation :as validation]))

(def ^:private root-path [:Task :Transaction :DocumentData])
(def ^:private import-dir (env :abacus-import-dir))
(def ^:private export-dir (env :abacus-export-dir))

(def ^:private param-mapping
  {:productNumber [:artikel_nr text]
   :title [:MetaData :dc :title text]
   :creator [:MetaData :dc :creator text]
   :date [:MetaData :dc :date text]
   :source [:MetaData :dc :source text]
   :language [:MetaData :dc :language text]
   :sourcePublisher [:MetaData :ncc :sourcePublisher text]
   :sourceEdition [:MetaData :ncc :sourceDate text]
   ;;   :volumes [:MetaData :ncc :setInfo text] ; not sure if this is reliable
   :revisionDate [:MetaData :ncc :revisionDate text]
   })

(defn read-file
  "Read an export file from ABACUS and return a map with all the data"
  [file]
  (let [zipper (-> file io/file xml/parse zip/xml-zip)]
    (into {} (for [[key path] param-mapping
                   :let [val (apply xml1-> zipper (concat root-path path))]]
               [key val]))))

(defn file-startswith? [f xs]
  (some #(.startsWith (.getName f) %) xs))

(defn get-all-files []
  "Return all files from the `import-dir`"
  (filter #(.isFile %)
          (file-seq (io/file import-dir))))

(defn delete-files!
  "Delete all given `files`"
  [files]
  (doseq [f files] (io/delete-file f)))

(defn open-production?
  "Is the given `file` an export for opening a production?"
  [file]
  (file-startswith? file ["SN1_" "SN10_"]))

(defn import-new-production
  "Import a new production from file `f`"
  [f]
  (msg/publish queues/create-queue (read-file f))
  f)

(defn import-new-productions
  "Import new productions from ABACUS and put them on the create queue"
  []
  (->>
   (get-all-files)
   (filter open-production?)
   (filter validation/valid-open?)
   (map import-new-production)
   (delete-files!)))

(defn recorded-production?
  "Is the given `file` an export for a production that has been recorded?"
  [file]
  (file-startswith? file ["SN3_" "SN12_"]))

(defn import-recorded-production
  "Import a recorded production from file `f`"
  [f]
  (let [{product-number :productNumber} (read-file f)
        production (production/find-by-productnumber product-number)]
    (msg/publish queues/encode-queue production)
    f))

(defn import-recorded-productions
  "Import recorded productions from ABACUS and put them on the archive queue"
  []
  (->>
   (get-all-files)
   (filter recorded-production?)
   (filter validation/valid-recorded?)
   (map import-recorded-production)
   (delete-files!)))

(defn status-request?
  "Is the given `file` an status request for a production?"
  [file]
  (file-startswith? file ["SNStatus_"]))

(defn import-status-request [f]
  (let [{product-number :productNumber} (read-file f)
        production (production/find-by-productnumber product-number)]
    (msg/publish queues/notify-abacus-queue production)
    f))

(defn import-status-requests
  "Import status requests from ABACUS and put them on the queue"
  []
  (->>
   (get-all-files)
   (filter status-request?)
   (filter validation/valid-status-request?)
   (map import-status-request)
   (delete-files!)))

(defn metadata-update?
  "Is the given `file` an metadata update for a production?"
  [file]
  (file-startswith? file ["SNMeta_"]))

(defn import-metadata-update [f]
  (msg/publish queues/metadata-update (read-file f))
  f)

(defn import-metadata-updates
  "Import metadata updates from ABACUS and put them on the queue"
  []
  (->>
   (get-all-files)
   (filter metadata-update?)
   (filter validation/valid-metadata-sync?)
   (map import-metadata-update)
   (delete-files!)))

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
        (create-row 107 "") ; the Document Status Madras is no longer
                            ; used but ABACUS still expects it. Just
                            ; give it an empty string to chew on
        (create-row 276 audioFormat)
        ;; FIXME: the number of CDs is only determined at the time of
        ;; archiving. Is this persisted in the db?
        (create-row 275 volumes) ; Number of CDs
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
  (let [file-name (.getPath (io/file export-dir "Ax_" productNumber ".txt"))]
    (spit file-name (export production))))
