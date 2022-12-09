(ns mdr2.production
  "Functionality for productions"
  (:refer-clojure :exclude [find])
  (:require [clojure.java.io :refer [file]]
            [babashka.fs :as fs]
            [java-time :as time]
            [clojure.core.async :refer [>!!]]
            [conman.core :as conman]
            [mdr2.queues :as queues]
            [mdr2.db.core :as db]
            [mdr2.dtb :as dtb]
            [mdr2.production.path :as path]
            [mdr2.obi :as obi]
            [mdr2.pipeline1 :as pipeline]
            [clojure.tools.logging :as log]
            [medley.core :as medley]
            [iapetos.collector.fn :as prometheus]
            [mdr2.metrics :as metrics]))

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

(defn has-manifest?
  "Lazy version of [[manifest?]]. Only check the file system if the
  production meets certain criteria (state, library_number, etc)"
  [production]
  (and (= (:state production) "structured")
       (or (:library_number production)
           (> (:revision production) 0))
       (manifest? production)))

(defn manifest-validate
  "Return a list of validation errors for all DAISY exports of given
  `production`. If there are no validation errors or no DAISY exports
  returns an empty list"
  [production]
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

(defn manual-split?
  "Return true if the production has a manual split"
  [production]
  (fs/exists? (path/split-path production 1)))

(defn has-manual-split?
  "Lazy version of [[manual-split?]] Only check the file system if the
  production meets certain criteria (state)"
  [production]
  ;; we only really need to query the file system if the state
  ;; is "pending-split". In all other cases nobody cares whether the
  ;; split-path exists
  (and (= (:state production) "pending-split")
       (manual-split? production)))

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
     (medley/assoc-some
      m k
      (cond
        ;; ignore all parsing errors. If there are faulty values we simply do not use them
        (#{:date :produced_date :revision_date} k) (try (time/local-date v) (catch Exception _ nil))
        ;; source_date is just a year string
        (#{:source_date} k) (try (time/local-date (time/year v)) (catch Exception _ nil))
        (#{:volumes :library_record_id} k) (try (Integer/parseInt v) (catch Exception _ nil))
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
      (let [permissions (fs/str->posix "rwxrwxr--")]
        (fs/set-posix-file-permissions dir permissions)))))

(defn create!
  "Create a production"
  [production]
  ;; make sure nothing is inserted in the db if we cannot create the
  ;; dirs
  (conman/with-transaction [db/*db*]
    (let [p (-> production
                add-default-meta-data
                db/insert-production)]
      (create-dirs p)
      (when (:product_number p)
        ;; notify the erp of the status change
        (>!! queues/notify-abacus p))
      p)))

(defn remove-null-values [production]
  (medley/remove-vals nil? production))

(defn get-production [id]
  (-> {:id id}
      db/get-production
      remove-null-values))

(defn update! [production]
  (db/update-production production))

(defn find-by-productnumber
  "Find a production given its `product_number`"
  [product_number]
  (db/find-production {:product_number product_number}))

(defn set-state!
  "Set `production` to `state`"
  [production state]
  (log/debugf "Setting production state of %s to %s" (:id production) state)
  (let [p (assoc production :state state)]
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
  (as-> production p
    (assoc p :volumes volumes)
    (set-state! p "split")
    (>!! queues/encode
         {:production p
          :sample-rate sample-rate
          :bitrate bitrate})))

(defn set-state-cataloged! [production]
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
  (if (obi/directory-valid? production)
    ;; only delete if nothing is fishy with the directories
    (do (log/debugf "Cleaning up all directories for %s" (:id production))
        (doseq [dir (path/all production)] (fs/delete-tree dir)))
    (let [message (format "Failed to remove invalid Obi directory (%s)" (:id production))]
      (log/error message)
      ;; remove all but the recording directories
      (doseq [dir (path/all-but-recording production)] (fs/delete-tree dir))
      (throw (ex-info message {:error-id :invalid-obi-directory})))))

(defn set-state-archived! [production]
  (try
    (delete-all-dirs! production)
    ;; ignore deletion problems, i.e. archive the production even if
    ;; we couldn't delete the directories
    (catch clojure.lang.ExceptionInfo _))
  (set-state! production "archived"))

(defn repair!
  [production]
  (>!! queues/repair production))

(defn delete!
  "Delete a `production`"
  [production]
  (conman/with-transaction [db/*db*]
    (-> production
        ;; we do not actually delete anything in the db
        (set-state! "deleted")
        delete-all-dirs!)))

(defn add-structure
  "Add a DTBook XML to a `production`. This will also set the status
  to :structured"
  [production f]
  (try
    ;; move the file to the right place
    (fs/move f (xml-path production) {:replace-existing true})
    (catch java.nio.file.FileSystemException e
      (throw (ex-info (format "Couldn't upload Structure XML to %s" (xml-path production)) {:error-id ::structure-directory-missing}))))
  (set-state-structured! production))

(prometheus/instrument! metrics/registry #'create!)
(prometheus/instrument! metrics/registry #'get-production)
(prometheus/instrument! metrics/registry #'delete!)
(prometheus/instrument! metrics/registry #'set-state-cataloged!)
(prometheus/instrument! metrics/registry #'repair!)
(prometheus/instrument! metrics/registry #'add-structure)
(prometheus/instrument! metrics/registry #'delete-all-dirs!)
