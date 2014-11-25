(ns mdr2.production.path
  "Define the path to the various artifacts of a production"
  (:require [clojure.java.io :refer [file]]
            [environ.core :refer [env]]))

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
  "Path to the directory where the iso of the exported DTB for given
  `production` is placed"
  [{id :id}]
  (.getPath (file production-path "iso" (str id))))

(defn iso-name
  "Path to the iso of the exported DTB for given `production`"
  [{id :id :as production}]
  (.getPath (file (iso-path production) (str id ".iso"))))

(defn manifest-path
  "Path to the manifest of the DTB which was exported from obi for
  given `production`"
  [production]
  (.getPath (file (recorded-path production) "package.opf")))

(defn all
  "Return all the paths needed for a particular `production`"
  [production]
  (map #(% production)
       [structured-path recording-path recorded-path encoded-path iso-path]))
