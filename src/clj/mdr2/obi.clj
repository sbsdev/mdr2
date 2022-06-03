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

(defn directory-valid?
  "Validate an Obi working directory. Check if it contains
  `obiconfig.xml`, `project.obi`, a `Data` and a `Backup` directory.
  Also ensure that it doesn't contain any numerical subdirectories.
  Those might be other Obi projects."
  [production]
  (let [root-dir (file (path/structured-path production) "obiconfig.xml")]
    (fs/exists? root-dir)))
