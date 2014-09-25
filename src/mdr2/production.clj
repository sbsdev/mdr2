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

(defn path
  "Return the path for a given `id`. This is a directory where the
  relevant files are stored"
  [id]
  (.getPath (file production-path (str id))))

(defn xml-path
  "Return the path to the meta data XML file, i.e. the DTBook file for a given `id`"
  [id]
  (let [file-name (str id ".xml")
        path (path id)]
    (.getPath (file path file-name))))

(defn create
  "Create a production"
  [production]
  (as-> production p
        (merge p {:state (state/initial-state)})
        (db/add p)
        (fs/mkdirs (path (:id p)))))

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
        (db/add-or-update! p)
        (fs/mkdirs (path (:id p)))))

(defn update! [production]
  (db/update! production))

(defn find
  "Find a production given its `id`"
  [id]
  (db/find id))

(defn find-by-productnumber
  "Find a production given its `productNumber`"
  [productnumber]
  (db/find-by-productnumber productnumber))

(defn find-all
  "Find all productions"
  []
  (db/find-all))

(defn delete
  "Delete a production with the given `id`"
  [id]
  (db/delete id)
  (fs/delete-dir (path id)))

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
