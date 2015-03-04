(ns mdr2.dtbook
  "Functionality around [DTBook XML](http://www.daisy.org/z3986/2005/Z3986-2005.html)"
  (:require [clojure.java.io :refer [file]]
            [clojure.string :as s]
            [clojure.data.xml :as xml]
            [mdr2.data.xml :as xml-new]
            [mdr2.production.path :as path]))

;; FIXME: we should probably use some ready made library for i18n.
;; There is https://github.com/ptaoussanis/tower,
;; https://github.com/asmala/clj18n or
;; https://github.com/feldi/clojure-i18n. The first one seems like the
;; most mature but has a number of weird quirks and dependencies
(def ^:private translations
  {:de
   {:about "Zu diesem DAISY-Buch"
    :bib "Bibliographische Angaben"
    :description "Produktbeschreibung des Hörbuchverlages"
    :blurb "Klappentexte"
    :contributors "Mitwirkende"
    :enclosures "Hörbuchbeilagen"
    :end "Ende des Buches"}
   :en
   {:about "About this DAISY book"
    :bib "Bibliographic information"
    :description "Product description by talking book publisher"
    :blurb "Blurbs"
    :contributors "Contributors"
    :enclosures "Talking book enclosures"
    :end "End of book"}})

(def ^:private doctype
  (str
   "<!DOCTYPE dtbook PUBLIC "
   "\"-//NISO//DTD dtbook 2005-3//EN\" "
   "\"http://www.daisy.org/z3986/2005/dtbook-2005-3.dtd\">"))

(defn- translate
  "Return a string for the given `key` and `language`"
  [language key]
  (let [l (keyword language)
        locale (if (contains? translations l) l :de)]
    (get-in translations [locale key] key)))

(defn default-book
  "Return an default book sexp"
  [title creator]
  [:book
   [:frontmatter
    [:doctitle title]
    [:docauthor creator]
    [:level1 [:p]]]
   [:bodymatter
    [:level1 [:h1] [:p]]]])

(defn commercial-audiobook
  "Return an sexp for the body of a commercial audiobook"
  [title creator language]
  (let [t (partial translate language)]
    [:book
     [:frontmatter
      [:doctitle title]
      [:docauthor creator]
      [:level1
       [:h1 (t :about)]
       [:p]]
      [:level1
       [:h1 (t :bib)]
       [:p]]
      [:level1
       [:h1 (t :description)]
       [:p]]
      [:level1
       [:h1 (t :blurb)]
       [:p]]]
     [:bodymatter
      [:level1 [:h1 "[Audioimport]"] [:p]]]
     [:rearmatter
      [:level1
       [:h1 (t :contributors)]
       [:p]]
      [:level1
       [:h1 (t :enclosures)]
       [:p]]
      [:level1
       [:h1 (t :end)]
       [:p]]]]))

(defn- dtbook-sexp
  [{:keys [title creator subject description publisher date type format identifier
           source language rights
           source_date source_edition source_publisher source_rights source_title
           multimedia_type multimedia_content narrator produced_date
           revision revision_date revision_description
           total_time audio_format
           library_number]}] ; only books imported from the libray have this
  [:dtbook {:xmlns "http://www.daisy.org/z3986/2005/dtbook/"
            :version "2005-3" :xml:lang language}
   [:head
    [:meta {:name "dc:Title" :content title}]
    [:meta {:name "dc:Creator" :content creator}]
    [:meta {:name "dc:Subject" :content subject}]
    [:meta {:name "dc:Description" :content description}]
    [:meta {:name "dc:Publisher" :content publisher}]
    [:meta {:name "dc:Date" :content date}]
    [:meta {:name "dc:Type" :content type}]
    [:meta {:name "dc:Format" :content format}]
    [:meta {:name "dc:Identifier" :content identifier}]
    [:meta {:name "dc:Source" :content source}]
    [:meta {:name "dc:Language" :content language}]
    [:meta {:name "dc:Rights" :content rights}]
    [:meta {:name "dtb:uid" :content identifier}]
    [:meta {:name "dtb:sourceDate" :content source_date}]
    [:meta {:name "dtb:sourceEdition" :content source_edition}]
    [:meta {:name "dtb:sourcePublisher" :content source_publisher}]
    [:meta {:name "dtb:sourceRights" :content source_rights}]
    [:meta {:name "dtb:sourceTitle" :content source_title}]
    [:meta {:name "dtb:multimediaType" :content multimedia_type}]
    [:meta {:name "dtb:multimediaContent" :content multimedia_content}]
    [:meta {:name "dtb:narrator" :content narrator}]
    [:meta {:name "dtb:producer" :content publisher}]
    [:meta {:name "dtb:producedDate" :content produced_date}]
    [:meta {:name "dtb:revision" :content revision}]
    [:meta {:name "dtb:revisionDate" :content revision_date}]
    [:meta {:name "dtb:revisionDescription" :content revision_description}]
    [:meta {:name "dtb:totalTime" :content total_time}]
    [:meta {:name "dtb:audioFormat" :content audio_format}]]
   (if library_number
     (commercial-audiobook title creator language)
     (default-book title creator))])

(defn dtbook
  "Create a minimal DTBook XML template according to [the spec](http://www.daisy.org/z3986/2005/Z3986-2005.html) for a given `production`"
  [production]
  (-> production
      dtbook-sexp
      xml/sexp-as-element
      (xml-new/emit-str :doctype doctype)))

(defn dtbook-file
  "Write a default dtbook file to the structured-path for the given
  `production`"
  [production]
  (let [file-name (str (:id production) ".xml")
        dtbook-file-name (file (path/structured-path production) file-name)]
    (spit dtbook-file-name (dtbook production))))
