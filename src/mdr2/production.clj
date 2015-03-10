(ns mdr2.production
  "Functionality for productions"
  (:refer-clojure :exclude [find])
  (:require [clojure.java.io :refer [file]]
            [me.raynes.fs :as fs]
            [org.tobereplaced.nio.file :as nio]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :refer [to-date]]
            [environ.core :refer [env]]
            [immutant.messaging :as msg]
            [mdr2.queues :as queues]
            [mdr2.db :as db]
            [mdr2.dtb :as dtb]
            [mdr2.production.path :as path]
            [mdr2.obi :as obi]
            [mdr2.pipeline1 :as pipeline])
  (:import java.nio.file.StandardCopyOption))

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
  (if (not (manifest? production))
    []
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

(defn uuid
  "Return a randomly generated UUID optionally prefixed with `prefix`"
  ([] (uuid "ch-sbs-"))
  ([prefix] (str prefix (java.util.UUID/randomUUID))))

(defn default-meta-data
  "Return default meta data"
  []
  {:date (to-date (t/now))
   :language "de"
   :identifier (uuid)
   :volumes 1
   :type "Text"
   :format "ANSI/NISO Z39.86-2005"})

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
        (#{:date :source_date :produced_date :revision_date} k)
        (to-date (f/parse v))
        (#{:volumes} k) (Integer/parseInt v)
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
    (fs/mkdirs dir)
    ;; make sure recording and recorded are group writable
    (when (#{path/recorded-path path/recording-path} dir)
      (let [permissions (conj (nio/posix-file-permissions dir)
                              (nio/posix-file-permission :group-write))]
        (nio/set-posix-file-permissions! dir permissions)))))

(defn create
  "Create a production"
  [production]
  (let [p (-> production
              add-default-meta-data
              db/insert!)]
    (create-dirs p)
    p))

(defn update-or-create!
  [production]
  (let [new-production
        (as-> production p
          (add-default-meta-data p)
          ;; if a production doesn't have an id yet, e.g. in the case of
          ;; a bulk import, the id is assigned in the db insert. For
          ;; that reason we need the as-> macro to thread the result of
          ;; the insert into the next function
          ;; FIXME: we should delay the insert into the db until the
          ;; dirs have been created, i.e. if anything the view fails the
          ;; db should be rolled back. Kinda like the
          ;; @transaction.commit_on_success annotation in Django
          ;; FIXME: shouldn't we update the DTBook XML when the meta
          ;; data is updated?
          (db/update-or-insert! p))]
    (create-dirs new-production)
    new-production))

(defn update! [production]
  (db/update! production))

(defn find
  "Find a production given its `id`"
  [id]
  (first (db/find {:id id})))

(defn find-all-in-production
  "Find all productions which are currently in production, i.e. which
  haven't been archived or deleted"
  []
  (db/find-all-in-production))

(defn find-by-productnumber
  "Find a production given its `product_number`"
  [product_number]
  (first (db/find-by-productnumber {:product_number product_number})))

(defn find-by-title-or-creator
  "Find productions given a search `term`. Search in title and creator"
  [term]
  (db/find-by-title-or-creator {:term term}))

(defn find-by-library-signature
  "Find a production given a `library_signature`"
  [library_signature]
  (first (db/find-by-library-signature {:library_signature library_signature})))

(defn find-by-state
  "Find all productions with the given `state`"
  [state]
  (db/find-by-state {:state state}))

(defn set-state!
  "Set `production` to `state`"
  [production state]
  (let [p (assoc production :state state)]
    (update! p)
    (when (:product_number p)
      ;; notify the erp of the status change
      (msg/publish (queues/notify-abacus) p))
    p))

(defn set-state-structured! [production]
  ;; create a config file for obi
  (obi/config-file production)
  (set-state! production "structured"))

(defn set-state-recorded! [production]
  (let [new-production
        (as-> production p
          (merge p
                 (dtb/meta-data (path/recorded-path p))
                 {:produced_date (to-date (t/now))})
          (set-state! p "recorded"))]
    (msg/publish (queues/encode) {:production new-production})
    new-production))

(defn set-state-split! [production volumes sample-rate bitrate]
  (as-> production p
    (assoc p :volumes volumes)
    (set-state! p "split")
    (msg/publish (queues/encode)
                 {:production p
                  :sample-rate sample-rate
                  :bitrate bitrate})))

(defn set-state-cataloged! [production]
  (as-> production p
    (set-state! p "cataloged")
    (msg/publish (queues/archive) p)))

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
      (msg/publish (queues/archive) p))
    ;; when a normal production is encoded we set the state to encoded
    ;; and wait for the library to assign a library
    ;; signature (e.g. "ds123") to it
    (set-state! production "encoded")))

(defn delete-all-dirs!
  "Delete all artifacts on the file system for a production"
  [production]
  (doseq [dir (path/all production)] (fs/delete-dir dir)))

(defn set-state-archived! [production]
  (delete-all-dirs! production)
  (set-state! production "archived"))

(defn delete!
  "Delete a production with the given `id`"
  [id]
  (db/delete! {:id id})
  (delete-all-dirs! {:id id}))

(defn add-structure
  "Add a DTBook XML to a `production`. This will also set the status
  to :structured"
  [production f]
  ;; move the file to the right place
  (fs/move f (xml-path production) StandardCopyOption/REPLACE_EXISTING)
  (set-state-structured! production))
