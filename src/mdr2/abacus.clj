(ns mdr2.abacus
  (:require [clj-xpath.core :refer [xml->doc $x:text]]))

(def root "/AbaConnectContainer/Task/Transaction/DocumentData/")

(def param-mapping
  {:product-number "artikel_nr"
   :title "MetaData/dc/title"
   :creator "MetaData/dc/creator"
   :date "MetaData/dc/date"
   :source "MetaData/dc/source"
   :language "MetaData/dc/language"
   ;; :source_edition "MetaData/sbs/auflageJahr"
   ;; :source_publisher "MetaData/sbs/verlag"
   })

(defn read-file [file]
  (let [xml (-> file slurp xml->doc)]
    (into {} (for [[key path] param-mapping] 
               [key ($x:text (str root path) xml)]))))

