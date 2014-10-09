(ns mdr2.production
  "Functionality for productions"
  (:refer-clojure :exclude [find])
  (:require [clojure.java.io :refer [file]]
            [me.raynes.fs :as fs]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [environ.core :refer [env]]
            [mdr2.db :as db]
            [mdr2.state :as state]))

(def ^:private default-publisher "Swiss Library for the Blind, Visually Impaired and Print Disabled")
(def ^:private default-date-formatter (f/formatters :date))

(def production-path (env :production-path))

(defn structured-path
  "The path to the files relevant to a production that has been structured"
  [{id :id}]
  (file production-path "structured" (str id)))

(defn recording-path
  "The path to the files relevant to a production that is being recorded"
  [{id :id}]
  (file production-path "recording" (str id)))

(defn recorded-path
  "The path to the files relevant to a production that has been recorded"
  [{id :id}]
  (file production-path "recorded" (str id)))

(defn encoded-path
  "Path to the encoded version of the exported DTB, i.e. the DTB
  containing mp3s for given `production`"
  [{id :id}]
  (.getPath (file production-path "encoded" (str id))))

(defn iso-path
  "Path to the directory where the iso of the exported DTB for given `production` is placed"
  [{id :id}]
  (.getPath (file production-path "iso" (str id))))

(defn iso-name
  "Path to the iso of the exported DTB for given `production`"
  [{id :id :as production}]
  (.getPath (file (iso-path production) (str id ".iso"))))

(defn iso?
  "Return true if the production has an iso export"
  [production]
  (fs/exists? (iso-name production)))

(defn xml-path
  "Return the path to the meta data XML file, i.e. the DTBook file for a given production"
  [{id :id :as production}]
  (let [file-name (str id ".xml")
        path (structured-path production)]
    (.getPath (file path file-name))))

(defn manifest-path
  "Path to the manifest of the DTB which was exported from obi for
  given `production`"
  [production]
  (.getPath (file (recorded-path production) "package.opf")))

(defn manifest?
  "Return true if the production has a DAISY export"
  [production]
  (fs/exists? (manifest-path production)))

(defn create
  "Create a production"
  [production]
  (as-> production p
        (merge p {:state (state/initial-state)})
        (db/insert! p)
        (fs/mkdirs (structured-path p))))

(defn update-or-create!
  [production]
  (as-> production p
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
        (fs/mkdirs (structured-path p))))

(defn update! [production]
  (db/update! production))

(defn find
  "Find a production given its `id`"
  [id]
  (db/find id))

(defn find-all
  "Find all productions"
  []
  (db/find-all))

(defn find-by-productnumber
  "Find a production given its `product_number`"
  [product_number]
  (db/find-by-productnumber product_number))

(defn find-by-state
  "Find all productions with the given `state`"
  [state]
  (db/find-by-state state))

(defn delete-all-dirs
  "Delete all artifacts on the file system for a production"
  [production]
  (doseq [d [(structured-path production)
             (recording-path production)
             (recorded-path production)
             (encoded-path production)
             (iso-path production)]]
    fs/delete-dir d))

(defn delete
  "Delete a production with the given `id`"
  [id]
  (db/delete id)
  (delete-all-dirs {:id id}))

(defn uuid
  "Return a randomly generated UUID optionally prefixed with `prefix`"
  ([] (uuid "ch-sbs-"))
  ([prefix] (str prefix (java.util.UUID/randomUUID))))

(defn default-meta-data
  "Return default meta data"
  []
  {:publisher default-publisher
   :date (f/unparse default-date-formatter (t/now))
   :identifier (uuid)})

(defn add-default-meta-data
  "Add the default meta data to a production"
  [production]
  (merge (default-meta-data) production))

(defn dam-number
  "Return an id for a production as it is expected by legacy systems"
  [{id :id}]
  (str "dam" id))
