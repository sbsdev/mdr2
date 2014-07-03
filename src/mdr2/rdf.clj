(ns mdr2.rdf
  (:require [clojure.data.xml :as xml]))

(defn- depth [production] 0)

(defn- files [production] 0)

(defn- size [production] 0)

(defn- page-front [production] 0)
(defn- page-normal [production] 0)
(defn- page-special [production] 0)

(defn- rdf-sexp [production]
  (let [{:keys [title creator subject description publisher date identifier source language rights 
                sourceDate sourceEdition sourcePublisher sourceRights sourceTitle
                multimediaType multimediaContent narrator producedDate 
                revision revisionDate revisionDescription
                totalTime audioFormat type
                footnotes prodnotes multi-volume sidebars
                tocItems]} production]
    [:rdf:DRF {:xmlns:rdf "http://www.w3.org/1999/02/22-rdf-syntax-ns#"
               :xmlns:dc "http://purl.org/dc/elements/1.1/"
               :xmlns:ncc "http://www.daisy.org/publications/specifications/"
               :xmlns:sbs "http://www.sbszh.ch/sbs/namespace"}
     ;; see also
     ;; http://www.daisy.org/z3986/specifications/daisy_202.html?q=publications/specifications/daisy_202.html#ncc
     [:rdf:Description
      [:dc:creator creator]
      [:dc:date date]
      [:dc:format "Daisy 2.02"] ;; this is only for DAISY 2.02
      [:dc:identifier identifier]
      [:dc:language language]
      [:dc:publisher publisher]
      [:dc:source source]
      [:dc:title title]
      [:dc:type type]
      [:ncc:charset "utf-8"]
      [:ncc:depth (depth production)]
      [:ncc:files (files production)]
      (when footnotes [:ncc:footnotes footnotes])
      [:ncc:kByteSize (size production)]
      [:ncc:multimediaType multimediaType]
      [:ncc:narrator narrator]
      [:ncc:pageFront (page-front production)]
      [:ncc:pageNormal (page-normal production)]
      [:ncc:pageSpecial (page-special production)]
      (when prodnotes [:ncc:prodNotes prodnotes])
      [:ncc:producedDate producedDate]
      [:ncc:revisionDate revisionDate]
      (when multi-volume
        [:ncc:setInfo (str (:number multi-volume)) " of " (:total multi-volume)])
      (when sidebars [:ncc:sidebars sidebars])
      [:ncc:sourceDate sourceDate]
      [:ncc:sourcePublisher sourcePublisher]
      [:ncc:tocItems tocItems]
      [:ncc:totalTime totalTime]
      [:sbs:audioFormat "FIXME:"]
      [:sbs:audioTotalTime "FIXME:"]
      [:sbs:audioType "FIXME:"]
      [:sbs:idMaster "FIXME:"]
      [:sbs:idPrint "FIXME:"]
      [:sbs:idVorstufe "FIXME:"]
      [:sbs:kostenstelle "FIXME:"]
      [:sbs:printPageNumber "FIXME:"]]]))

(defn rdf 
  "Create rdf for a given production"
  [production]
  (-> production
      rdf-sexp
      xml/sexp-as-element
      xml/emit-str))
