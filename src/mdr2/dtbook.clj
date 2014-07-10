(ns mdr2.dtbook
  "Functionality around [DTBook XML](http://www.daisy.org/z3986/2005/Z3986-2005.html)"
  (:require [clojure.data.xml :as xml]))

(defn- dtbook-sexp 
  [{:keys [title creator subject description publisher date identifier source language rights 
           sourceDate sourceEdition sourcePublisher sourceRights sourceTitle
           multimediaType multimediaContent narrator producedDate 
           revision revisionDate revisionDescription
           totalTime audioFormat]}]
  [:dtbook {:xmlns "http://www.daisy.org/z3986/2005/dtbook/"
            :version "2005-3" :xml:lang language}
   [:head
    [:head {:name "dc:Title" :content title}]
    [:head {:name "dc:Creator" :content creator}]
    [:head {:name "dc:Subject" :content subject}]
    [:head {:name "dc:Description" :content description}]
    [:head {:name "dc:Publisher" :content publisher}]
    [:head {:name "dc:Date" :content date}]
    [:head {:name "dc:Type" :content "Text"}]
    [:head {:name "dc:Format" :content "ANSI/NISO Z39.86-2005"}]
    [:head {:name "dc:Identifier" :content identifier}]
    [:head {:name "dc:Source" :content source}]
    [:head {:name "dc:Language" :content language}]
    [:head {:name "dc:Rights" :content rights}]
    [:head {:name "dtb:uid" :content identifier}]
    [:head {:name "dtb:sourceDate" :content sourceDate}]
    [:head {:name "dtb:sourceEdition" :content sourceEdition}]
    [:head {:name "dtb:sourcePublisher" :content sourcePublisher}]
    [:head {:name "dtb:sourceRights" :content sourceRights}]
    [:head {:name "dtb:sourceTitle" :content sourceTitle}]
    [:head {:name "dtb:multimediaType" :content multimediaType}]
    [:head {:name "dtb:multimediaContent" :content multimediaContent}]
    [:head {:name "dtb:narrator" :content narrator}]
    [:head {:name "dtb:producer" :content publisher}]
    [:head {:name "dtb:producedDate" :content producedDate}]
    [:head {:name "dtb:revision" :content revision}]
    [:head {:name "dtb:revisionDate" :content revisionDate}]
    [:head {:name "dtb:revisionDescription" :content revisionDescription}]
    [:head {:name "dtb:totalTime" :content totalTime}]
    [:head {:name "dtb:audioFormat" :content audioFormat}]]
   [:book
    [:frontmatter
     [:doctitle title]
     [:docauthor creator]
     [:level1 [:p]]]
    [:bodymatter
     [:level1
      [:h1]
      [:p]]]]])

(defn dtbook 
  "Create a minimal DTBook XML template according to [the spec](http://www.daisy.org/z3986/2005/Z3986-2005.html) for a given `production`"
  [production]
  (-> production
      dtbook-sexp
      xml/sexp-as-element
      xml/emit-str))
