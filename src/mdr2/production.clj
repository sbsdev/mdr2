(ns mdr2.production
  "Functionality for productions"
  (:refer-clojure :exclude [find])
  (:require [mdr2.db :as db]
            [me.raynes.fs :as fs]
            [environ.core :refer [env]]))

(def production-path (env :production-path))

(defn path
  "Return the path for a given `id`"
  [id]
  (str production-path "/" id))

(defn create
  "Create a production"
  [{id :id :as p}]
  (db/add p)
  (fs/mkdir (path id)))

(defn find
  "Find a production given its `id` (i.e. product number)"
  [id]
  (db/find id))

(defn find-all
  "Find all productions"
  []
  (db/find-all))

(defn delete
  "Delete a production with the given `id`"
  [id]
  (db/delete id)
  (fs/delete-dir (path id)))
