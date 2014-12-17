(ns mdr2.production.path
  "Define the path to the various artifacts of a production"
  (:require [clojure.java.io :refer [file]]
            [environ.core :refer [env]]))

(def production-path (env :production-path))

(defn structured-path
  "The path to the files relevant to a production that has been
  structured"
  [{id :id}]
  (file production-path "structured" (str id)))

(defn recording-path
  "The path to the files relevant to a production that is being
  recorded"
  [{id :id}]
  (file production-path "recording" (str id)))

(defn split-path
  "The path to the files relevant to a `production` that has been
  manually split"
  ([{id :id}]
   (file production-path "split" (str id)))
  ([production volume]
   (file (split-path production) (str volume))))

(defn recorded-path
  "The path to the files relevant to a production that has been
  recorded"
  ([{id :id}]
   (file production-path "recorded" (str id)))
  ([production volume]
   (if volume
     (split-path production volume)
     (recorded-path production))))

(defn encoded-path
  "Path to the encoded version of the exported DTB, i.e. the DTB
  containing mp3s for given `production` and `volume`"
  ([{id :id}]
   (file production-path "encoded" (str id)))
  ([production volume]
   (file (encoded-path production) (str volume))))

(defn iso-path
  "Path to the directory where the iso of the exported DTB for given
  `production` and `volume` is placed"
  ([{id :id}]
   (file production-path "iso" (str id)))
  ([production volume]
   (file (iso-path production) (str volume))))

(defn iso-name
  "Path to the iso of the exported DTB for given `production` and
  `volume`"
  [{id :id :as production} volume]
  (file (iso-path production volume) (str id "_" volume ".iso")))

(defn manifest-path
  "Path to the manifest of the DTB which was exported from obi for
  given `production`."
  ([production]
   (manifest-path production nil))
  ([production volume]
   (file (recorded-path production volume) "package.opf")))

(defn all
  "Return all the paths needed for a particular `production`"
  [production]
  (map #(% production)
       [structured-path recording-path recorded-path split-path encoded-path iso-path]))
