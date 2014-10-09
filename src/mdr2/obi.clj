(ns mdr2.obi
  "Functionality around [obi](http://www.daisy.org/project/obi)"
  (:require [clojure.java.io :refer [file]]
            [clojure.data.xml :as xml]
            [clojure.string :as s]
            [mdr2.production.path :as path]))

(defn- config-sexp
  [production]
  (let [recording-path (path/recording-path production)
        recorded-path (path/recorded-path production)]
    [:obiconfig
     [:import
      [:obiprojectdirectory recording-path]
      [:audiosamplerate 44100]
      [:audiochannels 1]]
     [:export
      [:directory recorded-path]
      [:standard "daisy3"]
      [:audioencoding "wav"]
      [:audiosamplerate 44100]
      [:audiochannels 1]
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
