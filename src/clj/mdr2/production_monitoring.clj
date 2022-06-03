(ns mdr2.production-monitoring
  "Functionality to query some statistical information on all productions as a CSV"
  (:require [clojure.data.csv :as csv]
            [clojure.java.io :as io]
            [java-time :as time]
            [babashka.fs :as fs]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.dtb :as dtb]))

;; formatter for 2011-01-27 14:48:41.000000000
(def ^:private formatter (time/formatter "yyyy-MM-dd HH:mm:ss.SSSSSSSSS"))

(defn audio-length [production]
  ;; as it happens the function that calculates the audio-length for a
  ;; DTB is so generic that it can be reused as-is for obi projects
  (let [path (path/recording-path production)]
    (assoc production :audio-length (quot (dtb/audio-length path) 1000))))

(defn last-modified [production]
  (let [path (path/recording-path production)
        files (file-seq (io/file path))
        wav-files (filter dtb/wav-file? files)
        last (apply max 0 (map #(.toMillis (fs/last-modified-time %)) wav-files))
        last-time (time/zoned-date-time (time/instant last) (time/zone-id))
        timestamp (time/format formatter last-time)]
   (assoc production :last-modified timestamp)))

(defn total-audio-length [productions]
  (->> productions
   ;; for each production calculate the audio time
   (map audio-length)
   ;; for each production get the last modification
   (map last-modified)
   ;; convert the id to a dam number
   (map #(assoc % :dam-number (prod/dam-number %)))
   ;; extract the fields that we are interested in
   (map #(map % [:dam-number :last-modified :audio-length]))))

(defn csv
  "Generate a CSV containing the DAM number, the last modified date
  and the total time for all given `productions`. Return the file
  name of the generated CSV."
  [productions]
  (let [tmp-file (io/file (fs/create-temp-file {:prefix "psm" :suffix ".csv"}))]
    (as-> 
     productions prods
     ;; get the total audio length for all productions
     (total-audio-length prods)
     ;; prepend header
     (into [["damNR" "datelastmodified" "totaltime"]] prods)
     ;; store it in a csv
     (with-open [out-file (io/writer tmp-file)]
       (csv/write-csv out-file prods :separator \;)))
    (.getPath tmp-file)))
