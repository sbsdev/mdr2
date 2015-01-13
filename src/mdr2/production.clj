(ns mdr2.production
  "Functionality for productions"
  (:refer-clojure :exclude [find])
  (:require [clojure.java.io :refer [file]]
            [me.raynes.fs :as fs]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [clj-time.coerce :refer [to-date]]
            [environ.core :refer [env]]
            [immutant.messaging :as msg]
            [mdr2.queues :as queues]
            [mdr2.db :as db]
            [mdr2.dtb :as dtb]
            [mdr2.production.path :as path]
            [mdr2.obi :as obi])
  (:import java.nio.file.StandardCopyOption))

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
  (reduce (fn [m k]
            (if-let [value (k m)]
              (assoc m k (to-date (f/parse value))) m))
          production [:date :source_date :produced_date :revision_date]))

(defn create
  "Create a production"
  [production]
  (as-> production p
        (add-default-meta-data p)
        (db/insert! p)
        (doseq [dir (path/all p)] (fs/mkdirs dir))))

(defn update-or-create!
  [production]
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
        (db/update-or-insert! p)
        (doseq [dir (path/all p)] (fs/mkdirs dir))))

(defn update! [production]
  (db/update! production))

(defn find
  "Find a production given its `id`"
  [id]
  (first (db/find {:id id})))

(defn find-all
  "Find all productions"
  []
  (db/find-all))

(defn find-by-productnumber
  "Find a production given its `product_number`"
  [product_number]
  (first (db/find-by-productnumber {:product_number product_number})))

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

(defn set-state-recorded! [production]
  (as-> production p
    (merge p
           (dtb/meta-data (path/recorded-path p))
           {:produced_date (to-date (t/now))})
    (set-state! p "recorded")
    (msg/publish (queues/encode) {:production p})))

(defn set-state-split! [production volumes sample-rate bitrate]
  (as-> production p
    (assoc p :volumes volumes)
    (set-state! p "split")
    (msg/publish (queues/encode)
                 {:production p
                  :sample-rate sample-rate
                  :bitrate bitrate})))

(defn delete-all-dirs
  "Delete all artifacts on the file system for a production"
  [production]
  (doseq [dir (path/all production)] (fs/delete-dir dir)))

(defn delete!
  "Delete a production with the given `id`"
  [id]
  (db/delete! {:id id})
  (delete-all-dirs {:id id}))

(defn add-structure
  "Add a DTBook XML to a `production`. This will also set the status
  to :structured"
  [production f]
  ;; move the file to the right place
  (fs/move f (xml-path production) StandardCopyOption/REPLACE_EXISTING)
  ;; create a config file for obi
  (obi/config-file production)
  ;; update the status
  (update! (assoc production :state "structured")))
