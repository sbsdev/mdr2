(ns mdr2.rdf
  "Create RDF files containing meta data for a production.

  RDF files are needed to pass meta data to the archive."
  (:require [clojure.data.xml :as xml]
            [mdr2.production.path :as path]
            [mdr2.dtb :as dtb]))

(defn get-meta-data [production]
  (merge production (dtb/meta-data (path/recorded-path production))))

(defn- rdf-sexp 
  [{:keys [title creator subject description publisher date identifier source language rights 
           source_date source_edition source_publisher source_rights source_title
           multimedia_type multimedia_content narrator producer produced_date
           revision revision_date revision_description
           total_time audio_format type]}]
  [:rdf:DRF {:xmlns:rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
             :xmlns:dc "http://purl.org/dc/elements/1.1/"
             :xmlns:dtb "http://www.daisy.org/z3986/2005/dtbook/"}
   ;; see also
   ;; http://www.daisy.org/z3986/2005/Z3986-2005.html#Over
   [:rdf:Description
    [:dc:title title]
    [:dc:creator creator]
    [:dc:subject subject]
    [:dc:description description]
    [:dc:publisher publisher]
    ;; what about dc:Contributor?
    [:dc:date date]
    [:dc:type type]
    [:dc:format "ANSI/NISO Z39.86-2005"] ; we are talking about DAISY3 here
    [:dc:identifier identifier]
    [:dc:source source]
    [:dc:language language]
    [:dc:rights rights]
    [:dtb:sourceDate source_date]
    [:dtb:sourceEdition source_edition]
    [:dtb:sourcePublisher source_publisher]
    [:dtb:sourceRights source_rights]
    [:dtb:sourceTitle source_title]
    [:dtb:multimediaType multimedia_type]
    [:dtb:multimediaContent multimedia_content]
    [:dtb:narrator narrator]
    [:dtb:producer producer]
    [:dtb:producedDate produced_date]
    [:dtb:revision revision]
    [:dtb:revisionDate revision_date]
    [:dtb:revisionDescription revision_description]
    [:dtb:totalTime total_time]
    [:dtb:audioFormat audio_format]]])

(defn rdf
  "Create rdf for a given production"
  [production]
  (-> production
      get-meta-data
      rdf-sexp
      xml/sexp-as-element
      xml/emit-str))
