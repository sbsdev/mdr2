(ns mdr2.production
  "Functionality for productions"
  (:refer-clojure :exclude [find])
  (:require [clojure.java.io :refer [file]]
            [babashka.fs :as fs]
            [java-time :as time]
            [clojure.core.async :refer [>!!]]
            [mdr2.queues :as queues]
            [mdr2.db.core :as db]
            [mdr2.dtb :as dtb]
            [mdr2.production.path :as path]
            [mdr2.obi :as obi]
            [mdr2.pipeline1 :as pipeline]
            [clojure.tools.logging :as log]
            [de.otto.nom.core :as nom]
            [medley.core :as medley]))

(defn library-signature?
  "Return true if `id` is a valid library signature"
  [id]
  (re-matches #"^ds\d{5,}$" id))

(defn multi-volume?
  "Return true if this production has multiple volumes"
  [production]
  (> (:volumes production) 1))

(defn xml-path
  "Return the path to the meta data XML file, i.e. the DTBook file for a given production"
  [{id :id :as production}]
  (let [file-name (str id ".xml")
        path (path/structured-path production)]
    (.getPath (file path file-name))))

(defn manifest?
  "Return true if the production has a DAISY export"
  [production]
  (fs/exists? (path/manifest-path production)))

(defn manifest-validate
  "Return a list of validation errors for all DAISY exports of given
  `production`. If there are no validation errors or no DAISY exports
  returns an empty list"
  [production]
  (log/debugf "Validating production %s" (:id production))
  (if (not (manifest? production))
    ["No manifest found"]
    (->> (range (:volumes production))
         (map inc)
         (map #(path/manifest-path production %))
         (map #(.getPath %))
         (map #(pipeline/validate % :daisy202))
         (apply concat))))

(defn manifest-valid?
  "Return true if all exports for this `production` are valid"
  [production]
  (empty? (manifest-validate production)))

(defn split?
  "Return true if the production has a manual split"
  [production]
  (fs/exists? (path/split-path production 1)))

(defn dam-number
  "Return an id for a production as it is expected by legacy systems"
  [{id :id}]
  (str "dam" id))

(defn smil-clock-value
  [{total_time :total_time}]
  "Return total_time for a `production` as a SMIL Clock Value
  according to http://www.daisy.org/z3986/2005/Z3986-2005.html#Clock,
  i.e. as \"3:22:55.91\""
  (let [time (or total_time 0)
        in-secs (quot time 1000)
        hours (quot in-secs 3600)
        minutes (quot (mod in-secs 3600) 60)
        seconds (mod in-secs 60)
        millis (mod time 1000)]
    (format "%02d:%02d:%02d.%03d" hours minutes seconds millis)))

(defn uuid
  "Return a randomly generated UUID optionally prefixed with `prefix`"
  ([] (uuid "ch-sbs-"))
  ([prefix] (str prefix (java.util.UUID/randomUUID))))

(defn default-meta-data
  "Return default meta data"
  []
  ;; FIXME: the comment below is probably wrong
  ;; we use sql date because its toString returns the format we want
  ;; in all the output (i.e. xml, etc)
  {:date (time/local-date)
   :language "de"
   :identifier (uuid)
   :volumes 1
   :type "Sound"
   :revision 0
   :depth 1
   :state "new"
   :format "Daisy 2.02"}) ; "ANSI/NISO Z39.86-2005" for DAISY3

(defn add-default-meta-data
  "Add the default meta data to a production"
  [production]
  (merge (default-meta-data) production))

(defn parse
  "Return a `production` with all values parsed into their proper types,
  e.g. dates are converted from strings to dates"
  [production]
  (reduce-kv
   (fn [m k v]
     (assoc m k
      (cond
        (#{:date :produced_date :revision_date} k) (time/local-date v)
        ;; source_date is just a year string
        (#{:source_date} k) (time/local-date (time/year v))
        (#{:volumes :library_record_id} k) (Integer/parseInt v)
        :else v)))
   {} production))

(defn create-dirs
  "Create all working dirs for a `production`"
  [production]
  (doseq [dir (->> production
               path/all
               ;; do not create the recording path as that is created
               ;; by obi
               (remove #{(path/recording-path production)}))]
    (fs/create-dirs dir)
    ;; make sure recording and recorded are group writable
    (when (or (#{(path/recorded-path production)
                 (path/recording-path production)
                 (path/split-path production)} dir)
              ;; FIXME: this is a temporary workaround. Until all
              ;; narrators have been migrated to obi etext needs write
              ;; access to the structured dir
              (and (= (:production_type production) "periodical")
                   (= (path/structured-path production) dir)))
      (let [permissions (fs/str->posix "rw-rw-r--")]
        (fs/set-posix-file-permissions dir permissions)))))

(defn create!
  "Create a production"
  [production]
  ;; FIXME: make sure nothing is inserted in the db if we cannot create the dirs
  ;;  (transaction)
  (nom/let-nom> [p (-> production
                       add-default-meta-data
                       db/insert-production)]
    (create-dirs p)
    (when (:product_number p)
      ;; notify the erp of the status change
      (>!! queues/notify-abacus p))))

(defn remove-null-values [production]
  (medley/remove-vals nil? production))

(defn update! [production]
  (db/update-production production))

(defn find-by-productnumber
  "Find a production given its `product_number`"
  [product_number]
  (db/find-production {:product_number product_number}))

(defn set-state!
  "Set `production` to `state`"
  [production state]
  (let [p (assoc production :state state)]
;;    (transaction)
    (update! p)
    (when (:product_number p)
      ;; notify the erp of the status change
      (>!! queues/notify-abacus p))
    p))

(defn set-state-structured! [production]
  ;; create a config file for obi
  (obi/config-file production)
  (set-state! production "structured"))

(defn update-production-dates
  "Update the production dates for a new revision according to
  http://www.daisy.org/z3986/2005/Z3986-2005.html#PubMed"
  [production date]
  (-> production
      (assoc :date date :revision_date date)
      ;; only set the :produced_date for the first revision
      (conj (when (= (:revision production) 0) [:produced_date date]))))

(defn set-state-recorded! [production]
  (log/debugf "Setting production state of %s to recorded" (:id production))
;;  (transaction)
  (let [new-production
        (-> production
            (merge (dtb/meta-data (path/recorded-path production)))
            (update-production-dates (time/local-date))
            (update-in [:revision] inc)
            (set-state! "recorded"))]
    (log/debugf "Publishing %s to encode queue" (:id new-production))
    (>!! queues/encode {:production new-production})
    new-production))

(defn set-state-split! [production volumes sample-rate bitrate]
;;  (transaction)
  (as-> production p
    (assoc p :volumes volumes)
    (set-state! p "split")
    (>!! queues/encode
         {:production p
          :sample-rate sample-rate
          :bitrate bitrate})))

(defn set-state-cataloged! [production]
;;  (transaction)
  (as-> production p
    (set-state! p "cataloged")
    (>!! queues/archive p)))

(defn set-state-encoded! [{:keys [production_type] :as production}]
  (if (or (not= production_type "book")
           (:library_signature production))
    ;; if the production is not a book, i.e. a periodical or other it
    ;; will not be stored in the library system and hence doesn't need
    ;; a library signature. So we can go directly to archiving.

    ;; When repairing a production we already have a library
    ;; signature, so no need to ask the library for another one; go
    ;; directly to archiving
;;    (transaction)
    (as-> production p
      (set-state! p "cataloged")
      (>!! queues/archive p))
    ;; when a normal production is encoded we set the state to encoded
    ;; and wait for the library to assign a library
    ;; signature (e.g. "ds123") to it
    (set-state! production "encoded")))

(defn delete-all-dirs!
  "Delete all artifacts on the file system for a production"
  [production]
  (doseq [dir (path/all production)] (fs/delete-tree dir)))

(defn set-state-archived! [production]
;;    (transaction)
  (delete-all-dirs! production)
  (set-state! production "archived"))

(defn delete!
  "Delete a `production`"
  [production]
  ;; we do not actually delete anything in the db
  (-> production
      (set-state! "deleted")
      delete-all-dirs!))

(defn add-structure
  "Add a DTBook XML to a `production`. This will also set the status
  to :structured"
  [production f]
  ;; move the file to the right place
  (fs/move f (xml-path production) {:replace-existing true})
  (set-state-structured! production))
