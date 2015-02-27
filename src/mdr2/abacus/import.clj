(ns mdr2.abacus.import
  "Funtionality for the initial import of the production data from
  ABACUS to Madras2"
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.string :as string]
            [clojure.java.jdbc :as jdbc]
            [environ.core :refer [env]]
            [mdr2.production :as prod]))

(def ^:private db (env :database-url))

(defn fix-production
  "Fix a `production`, i.e. fix some of the fields as they are coing
  from ABACUS the values as they are expected by Madras2"
  [{:keys [id periodical_number] :as production}]
  (-> production
   (assoc :id (second (re-find #"dam(\d+)" id)))
   (assoc :production_type "book")
   (assoc :periodical_number
          (when-not (string/blank? periodical_number) periodical_number))
   (dissoc :Produktestatus)))

(defn productions
  "Given a file or a filename `f` return a lazy seq of productions"
  [f]
  (->> f
   io/reader
   csv/read-csv
   (drop 2) ; ignore headers
   (map #(zipmap [:product_number :creator :title :production_type
                  :periodical_number :source_publisher :source_date
                  :source :Produktestatus :volumes :id :multimedia_type
                  :narrator :produced_date :total_time :state :date :depth] %))
   (map prod/parse)
   (map fix-production)
   (map #(merge (prod/default-meta-data) %))))

(defn import!
  "Import all productions from file `f` and insert them in the data base"
  [f]
  (apply jdbc/insert! db :production (productions f)))
