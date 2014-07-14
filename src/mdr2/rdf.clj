(ns mdr2.rdf
  (:require [clojure.data.xml :as xml]
            [mdr2.dtb :as dtb]))

(defn get-meta-data [production]
  (merge production (dtb/meta-data production)))

(defn- rdf-sexp 
  [{:keys [title creator subject description publisher date identifier source language rights 
           sourceDate sourceEdition sourcePublisher sourceRights sourceTitle
           multimediaType multimediaContent narrator producer producedDate
           revision revisionDate revisionDescription
           totalTime audioFormat type]}]
  [:rdf:DRF {:xmlns:rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
             :xmlns:dc "http://purl.org/dc/elements/1.1/"
             :xmlns:ncc "http://www.daisy.org/publications/specifications/"
             :xmlns:sbs "http://www.sbszh.ch/sbs/namespace"}
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
    [:dtb:sourceDate sourceDate]
    [:dtb:sourceEdition sourceEdition]
    [:dtb:sourcePublisher sourcePublisher]
    [:dtb:sourceRights sourceRights]
    [:dtb:sourceTitle sourceTitle]
    [:dtb:multimediaType multimediaType]
    [:dtb:multimediaContent multimediaContent]
    [:dtb:narrator narrator]
    [:dtb:producer producer]
    [:dtb:producedDate producedDate]
    [:dtb:revision revision]
    [:dtb:revisionDate revisionDate]
    [:dtb:revisionDescription revisionDescription]
    [:dtb:totalTime totalTime]
    [:dtb:audioFormat audioFormat]]])

(defn rdf
  "Create rdf for a given production"
  [production]
  (-> production
      get-meta-data
      rdf-sexp
      xml/sexp-as-element
      xml/emit-str))
