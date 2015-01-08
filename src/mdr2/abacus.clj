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
            [clojure.tools.logging :as log]
            [org.tobereplaced.nio.file :as nio]
            [immutant.messaging :as msg]
            [environ.core :refer [env]]
            [mdr2.queues :as queues]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.abacus.validation :as validation])
  (:import (java.nio.file StandardCopyOption)))

(def ^:private root-path [:Task :Transaction :DocumentData])
(def ^:private import-dir (env :abacus-import-dir))
(def ^:private export-dir (env :abacus-export-dir))

(def ^:private param-mapping
  {:product_number [:artikel_nr text]
   :title [:MetaData :dc :title text]
   :creator [:MetaData :dc :creator text]
   :date [:MetaData :dc :date text]
   :source [:MetaData :dc :source text]
   :language [:MetaData :dc :language text]
   :source_publisher [:MetaData :ncc :sourcePublisher text]
   :source_edition [:MetaData :ncc :sourceDate text]
   :volumes [:MetaData :ncc :setInfo text]
   :revision_date [:MetaData :ncc :revisionDate text]
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
  (msg/publish (queues/create) (read-file f))
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

(defn move-away [f reason]
  (let [new-name (str reason "_" (.getName f))
        new-path (nio/resolve-sibling f new-name)]
    (nio/move! f new-path StandardCopyOption/REPLACE_EXISTING)))

(defn import-valid-recorded-production
  "Import a valid recorded production from file `f`"
  [f]
  (let [{product_number :product_number} (read-file f)
        production (prod/find-by-productnumber product_number)]
    (if (and production
             (= "structured" (:state production))
             (prod/manifest? production))
      (do
        (prod/set-state-recorded! production)
        (io/delete-file f))
      (let [message
            (cond
             (empty? production)
             (format "Non-existing product number %s in %s"
                     product_number (.getName f))
             (not= "structured" (:state production))
             (format "Production %s is not structured (%s instead) in %s"
                     product_number (:state production) (.getName f))
             (not (prod/manifest? production))
             (format "Production %s has no DAISY Export in %s"
                     product_number (path/manifest-path production)))]
        (log/warn message)
        (move-away f "Failed")))))

(defn import-recorded-production
  "Import a recorded production from file `f`"
  [f]
  (if (validation/valid-recorded? f)
    (import-valid-recorded-production f)
    (do
      (log/warnf "ABACUS input file %s not valid" (.getName f))
      (move-away f "Invalid"))))

(defn import-recorded-productions
  "Import recorded productions from ABACUS and put them on the archive queue"
  []
  (let [files (->> (get-all-files) (filter recorded-production?))]
    (doseq [f files] (import-recorded-production f))))

(defn status-request?
  "Is the given `file` an status request for a production?"
  [file]
  (file-startswith? file ["SNStatus_"]))

(defn import-status-request [f]
  (let [{product_number :product_number} (read-file f)
        production (prod/find-by-productnumber product_number)]
    (msg/publish (queues/notify-abacus) production)
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
  (msg/publish (queues/metadata-update) (read-file f))
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
      (list "D" section k (format "'%s'" v))))) ; all values need to be quoted

(def ^:private date-format "%1$td.%1$tm.%1$tY")

(defn export
  "Export the state of a production as a csv-like structure, ready to
  be consumed by ABACUS"
  [{:keys [product_number total_time state audio_format multimedia_type
           date id produced_date volumes] :as production}]
  (->> [(create-row 2 product_number)
        (create-row 239 (quot total_time (* 1000 60))) ; in minutes
        (create-row 106 state)
        (create-row 280 audio_format)
        (create-row 271 volumes) ; Number of CDs
        (create-row 281 multimedia_type)
        (create-row 256 (format date-format produced_date)) ; Date of production end
        (create-row 278 (format date-format date)) ; Date of production start
        (create-row 4 (prod/dam-number production))] ; for legacy purposes
       (remove nil?) ; remove empty rows
       wrap-rows ; wrap the payload
       (map-indexed #(conj %2 (inc %1))) ; number each row starting at 1
       (map #(string/join "," %))
       (string/join "\r\n"))) ; use cr/lf as this is consumed only on windows

(defn export-file
  "Export the state of a production into a special file in
  `export-dir`. The file is named with the `product_number`. Existing
  files are overwritten"
  [{product_number :product_number :as production}]
  ;; file names are supposed to be "Ax_product_number.txt, e.g. Ax_DY15000.txt"
  (let [file-name (.getPath (io/file export-dir (str "Ax_" product_number ".txt")))]
    (spit file-name (export production))))
