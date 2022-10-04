(ns mdr2.abacus.core
  "Import and export files to and from ABACUS

  The interface to our ERP is quite crude: you basically read and
  write files from an directory. Both the import from and the export
  to ABACUS are done via XML. The interface imports the following
  notifications:

  - Start (open) a production
  - a production is recorded
  - Meta data updates

  and the interface sends notifications to ABACUS when:

  - the state of a production changes
  - meta data of a production changes."
  (:require [clojure.java.io :as io]
            [clojure.xml :as xml]
            [clojure.data.xml :as data.xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml-> xml1-> attr= attr text]]
            [clojure.string :as string]
            [clojure.tools.logging :as log]
            [clojure.core.async :refer [>!!]]
            [mdr2.config :refer [env]]
            [mdr2.queues :as queues]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.abacus.validation :as validation]
            [iapetos.collector.fn :as prometheus]
            [mdr2.metrics :as metrics]))

(def ^:private root-path [:Task :Transaction :DocumentData])

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
   :library_record_id [:MetaData :ncc :VubisRecordID text]
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
                          :else "book")
        source-date (source-date raw-production)]
    (-> raw-production
        (dissoc :mvl_only :command :idVorstufe)
        (assoc :production_type production_type)
        (cond-> source-date (assoc :source_date source-date))
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
    (if (empty? errors)
      (prod/create! (read-file f))
      (throw
       (ex-info "The provided xml is not valid"
                {:error-id :invalid-xml
                 :errors errors})))))

(defn import-recorded-production
  "Import a recorded production from file `f`"
  [f]
  (log/debugf "Importing recorded production from file %s" f)
  (let [errors (validation/recorded-validation-errors f)]
    (if (empty? errors)
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
          (log/debugf "Importing recorded event for production %s" (:id production))
          (cond
            (empty? production)
            (throw (ex-info (format "Non-existing product number %s" product_number) {:error-id :product-not-found}))
            (not= "structured" state)
            (throw (ex-info (format "Production %s (%s) is not structured (%s instead)" id product_number state) {:error-id :invalid-state}))
            (not (prod/manifest? production))
            (throw (ex-info (format "Production %s (%s) has no DAISY Export in %s" id product_number (path/manifest-path production)) {:error-id :invalid-daisy-export}))
            :else
            ;; check if the exported production is even valid
            ;; for validation purposes pretend there is only one volume. At
            ;; this stage the number of volumes just indicates that there
            ;; should be a split into that many volumes. The volumes aren't
            ;; actually there yet
            (let [errors (prod/manifest-validate (assoc production :volumes 1))]
              (if (empty? errors)
                (-> production
                    (merge new-production)
                    prod/set-state-recorded!)
                (throw (ex-info "The exported production is not valid" {:error-id :invalid-exported-production :errors errors})))))))
      (throw (ex-info "The provided xml is not valid" {:error-id :invalid-xml :errors errors})))))

(defn import-status-request
  "Import a status request from file `f`"
  [f]
  (let [errors (validation/status-request-errors f)]
    (if (empty? errors)
      (let [{product_number :product_number} (read-file f)]
        (if-some [production (prod/find-by-productnumber product_number)]
          (do
            (>!! queues/notify-abacus production)
            production)
          (throw (ex-info (format "Product %s number not found" product_number)
                          {:error-id :product-not-found}))))
      (throw (ex-info "The provided xml is not valid"
                      {:error-id :invalid-xml :errors errors})))))

(defn import-metadata-update
  "Import a metadata update request from file `f`"
  [f]
  (let [errors (validation/metadata-sync-errors f)]
    (if (empty? errors)
      (let [production (read-file f)
            updates (-> production
                        ;; ignore production_type and the revision date when
                        ;; updating metadata
                        (dissoc :production_type :revision_date)
                        prod/update!)]
        (if (= 1 updates)
          updates
          ;; if the update could not find the product number it will return
          ;; 0 modified records. In that case return a failure
          (throw (ex-info (format "Product %s number not found" (:product_number production))
                          {:error-id :product-not-found}))))
      (throw (ex-info "The provided xml is not valid"
                      {:error-id :invalid-xml :errors errors})))))

(defn- export-sexp
  [{:keys [product_number total_time state audio_format
           multimedia_type volumes depth] :as production}]
  [:AbaConnectContainer
   [:TaskCount 1]
   [:Task
    [:Parameter
     [:Application "ORDE"]
    [:Id "Produktstamm"]
    [:MapId "AbaDefault"]
    [:Version "2015.00"]]
   [:Transaction {:id 1}
    [:ProductData {:mode "SAVE"}
     [:ProductDataFields {:mode "SAVE"}
      [:ProductNumber product_number]
      [:Ascii3 (string/capitalize state)]
      [:_USERFIELD13 (quot (or total_time 0) (* 1000 60))] ; in minutes
      [:_USERFIELD54 volumes]
      [:__USERFIELD53 depth]
      [:__USERFIELD54 audio_format]
      [:__USERFIELD55 multimedia_type]]
     [:ProductDataIndividualUserFields {:mode "SAVE"}
      [:ProductDataIndividualUserFieldsFields {:mode "SAVE"}
       [:Ascii1 (prod/dam-number production)]]]]]]])

(defn export
  "Export the state of a production, ready to be consumed by ABACUS"
  [production]
  (-> production
      export-sexp
      data.xml/sexp-as-element
      data.xml/indent-str))

(defn export-file
  "Export the state of a production into a special file in
  `:abacus-export-dir` in the environment. The file is named with the
  `product_number`. Existing files are overwritten"
  [{product_number :product_number :as production}]
  ;; file names are supposed to be "Ax_product_number.xml, e.g. Ax_DY15000.xml"
  (let [file-name (.getPath (io/file (env :abacus-export-dir) (str "Ax_" product_number ".xml")))]
    (spit file-name (export production))))

(prometheus/instrument! metrics/registry #'import-new-production)
(prometheus/instrument! metrics/registry #'import-recorded-production)
(prometheus/instrument! metrics/registry #'import-status-request)
(prometheus/instrument! metrics/registry #'import-metadata-update)
(prometheus/instrument! metrics/registry #'export-file)
