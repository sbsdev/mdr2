(ns mdr2.obi
  "Functionality around [obi](http://www.daisy.org/project/obi)"
  (:require [clojure.java.io :refer [file]]
            [clojure.data.xml :as xml]
            [clojure.string :as s]
            [mdr2.production.path :as path]
            [babashka.fs :as fs]))

(def ^:private network-drive
  "The windows network drive where the obi directories are mapped"
  "Y")

(defn- recording-path
  "Return the windows specific path to where the obi project is going
  to be created for the given production"
  [{id :id}]
  (format "%s:\\recording\\%s" network-drive id))

(defn- recorded-path
  "Return the windows specific path to where obi is going to export
  the given production"
  [{id :id}]
  (format "%s:\\recorded\\%s" network-drive id))

(defn- config-sexp
  [production]
  (let [recording-path (recording-path production)
        recorded-path (recorded-path production)
        audiochannels (if (:library_number production) 2 1)]
    [:obiconfig
     [:import
      [:obiprojectdirectory recording-path]
      [:audiosamplerate 44100]
      [:audiochannels audiochannels]]
     [:export
      [:directory recorded-path]
      [:standard "daisy2.02"]
      [:audioencoding "wav"]
      [:audiosamplerate 44100]
      [:audiochannels audiochannels]
      [:bitrate 64]]]))

(defn config
  "Create the configuration XML for a given `production` according to
  http://www.daisy.org/obi/configuration-file-documentation"
  [production]
  (-> production
      config-sexp
      xml/sexp-as-element
      xml/emit-str))

(defn config-file
  [production]
  (let [config-file-name (file (path/structured-path production) "obiconfig.xml")]
    (spit config-file-name (config production))))

(defn contains-obi-subdirs?
  "Return true if a `production` contains subdirs that contain a file
  named `obiconfig.xml`. If true this is a strong indication that
  something isn't quite right."
  [production]
  (let [root-dir (path/recording-path production)]
    (some? (seq (fs/glob root-dir "**/obiconfig.xml")))))

(defn contains-necessary-files?
  "Return true if a `production` contains at least `obiconfig.xml`,
  `project.obi`, a `Data` and a `Backup` directory."
  [production]
  (let [root-dir (path/recording-path production)]
    (and (->> ["obiconfig.xml" "project.obi"]
              (map (partial fs/path root-dir))
              (every? fs/exists?))
         (->> ["Data" "Backup"]
              (map (partial fs/path root-dir))
              (every? fs/directory?)))))

(defn directory-valid?
  "Validate an Obi working directory for given production. Return true
  if the [[path/recording-path]] is a directory, the production
  contains all necessary files (see [[contains-necessary-files?]]) and
  it does not contains any subdirs containing a
  `obiconfig.xml` (see [[contains-obi-subdirs?]])"
  [production]
  (and
   (fs/directory? (path/recording-path production))
   (contains-necessary-files? production)
   (not (contains-obi-subdirs? production))))
