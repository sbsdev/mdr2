(ns mdr2.production
  "Functionality for productions"
  (:refer-clojure :exclude [find])
  (:require [clojure.java.io :refer [file]]
            [me.raynes.fs :as fs]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [environ.core :refer [env]]
            [mdr2.db :as db]))

(def ^:private default-publisher "Swiss Library for the Blind, Visually Impaired and Print Disabled")
(def ^:private default-date-formatter (f/formatters :date))

(def production-path (env :production-path))

(defn path
  "Return the path for a given `id`"
  [id]
  (.getPath (file production-path (str id ".xml"))))

(defn create
  "Create a production"
  [{id :id :as p}]
  (db/add p)
  (fs/mkdirs (fs/parent (path id))))

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
  (fs/delete-dir (fs/parent (path id))))

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