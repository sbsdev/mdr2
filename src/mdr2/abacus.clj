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
            [clojure.data.zip.xml :refer [xml-> xml1-> attr= attr text]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [immutant.messaging :as msg]
            [environ.core :refer [env]]
            [mdr2.queues :as queues]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.abacus.validation :as validation]))

(def ^:private root-path [:Task :Transaction :DocumentData])
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
   :narrator [:MetaData :ncc :narrator text]
   :volumes [:MetaData :ncc :setInfo text]
   :revision_date [:MetaData :ncc :revisionDate text]
   :mvl_only [:mvl_only text]
   :command [(attr :command)]
   :idVorstufe [:MetaData :sbs :idVorstufe text]
   })

(defn source-date
  "Extract the source date from a raw `production` by taking the last
  segment of the `:source_edition`"
  [{source_edition :source_edition}]
  (when source_edition
    (-> source_edition
        (string/split #"/")
        last
        (->> (re-find #"\d{4}")))))

(defn clean-raw-production
  "Return a proper production based on a raw production, i.e. drop
  `:mvl_only`, `:command` and `:idVorstufe` and add `:production_type`
  and `:periodical_number`"
  [{:keys [mvl_only command idVorstufe] :as raw-production}]
  (let [production_type (cond
                          (= command "mdaDocAdd_Kleinauftrag") "other"
                          (= mvl_only "yes") "periodical"
                          :else "book")]
    (-> raw-production
        (dissoc :mvl_only :command :idVorstufe)
        (assoc :production_type production_type)
        (assoc :source_date (source-date raw-production))
        (cond-> (= production_type "periodical")
          (assoc :periodical_number idVorstufe)))))

(defn extract-value
  "Extract values from a `zipper` from an ABACUS export file for `key`"
  [zipper key]
  (let [path (key param-mapping)]
    (case key
      :narrator (string/join "; " (apply xml-> zipper (concat root-path path)))
      (apply xml1-> zipper (concat root-path path)))))

(defn read-file
  "Read an export file from ABACUS and return a map with all the data,
  i.e. a production"
  [file]
  (let [zipper (-> file io/file xml/parse zip/xml-zip)]
    (->>
     (for [key (keys param-mapping)
           :let [val (extract-value zipper key)]
           :when (some? val)]
       [key val])
      (into {})
      clean-raw-production
      prod/parse)))

(defn import-new-production
  "Import a new production from file `f`"
  [f]
  (let [errors (validation/open-validation-errors f)]
    (if (seq errors)
      errors
      (prod/create! (read-file f)))))

(defn import-recorded-production
  "Import a recorded production from file `f`"
  [f]
  (let [errors (validation/recorded-validation-errors f)]
    (if (seq errors)
      errors
      (let [{product_number :product_number :as new-production}
            (-> (read-file f)
                ;; ignore production_type and periodical_number when
                ;; importing a recorded production
                ;; periodical_number is wrong because the XML exported
                ;; for a recorded production lacks the mvl_only field
                ;; and hence the production_type and the
                ;; periodical_number will be wrong
                (dissoc :production_type :periodical_number))
            production (prod/find-by-productnumber product_number)]
        (let [{:keys [id state]} production]
          (cond
            (empty? production)
            [(format "Non-existing product number %s" product_number)]
            (not= "structured" state)
            [(format "Production %s (%s) is not structured (%s instead)"
                     id product_number state)]
            (not (prod/manifest? production))
            [(format "Production %s (%s) has no DAISY Export in %s"
                     id product_number (path/manifest-path production))]
            :else
            ;; check if the exported production is even valid
            ;; for validation purposes pretend there is only one volume. At
            ;; this stage the number of volumes just indicates that there
            ;; should be a split into that many volumes. The volumes aren't
            ;; actually there yet
            (let [errors (prod/manifest-validate (assoc production :volumes 1))]
              (if (seq errors)
                errors
                (-> production
                    (merge new-production)
                    prod/set-state-recorded!)))))))))

(defn import-status-request
  "Import a status request from file `f`"
  [f]
  (let [errors (validation/status-request-errors f)]
    (if (seq errors)
      errors
      (let [{product_number :product_number} (read-file f)
            production (prod/find-by-productnumber product_number)]
        (msg/publish (queues/notify-abacus) production)
        production))))

(defn import-metadata-update
  "Import a metadata update request from file `f`"
  [f]
  (let [errors (validation/metadata-sync-errors f)]
    (if (seq errors)
      errors
      (prod/update!
       (-> (read-file f)
           ;; ignore production_type and the revision date when
           ;; updating metadata
           (dissoc :production_type :revision_date))))))

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
           date id produced_date volumes depth] :as production}]
  (->> [(create-row 2 product_number)
        (create-row 239 (quot (or total_time 0) (* 1000 60))) ; in minutes
        (create-row 106 (string/capitalize state))
        (create-row 280 audio_format)
        (create-row 271 volumes) ; Number of CDs
        (create-row 279 depth)
        (create-row 281 multimedia_type)
        (create-row 256 (and produced_date
                             (format date-format produced_date))) ; Date of production end
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
