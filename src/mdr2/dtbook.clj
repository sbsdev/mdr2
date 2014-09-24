(ns mdr2.dtbook
  "Functionality around [DTBook XML](http://www.daisy.org/z3986/2005/Z3986-2005.html)"
  (:require [clojure.data.xml :as xml]
            [clojure.string :as s]))

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

(defn- fallback
  "Return the `language` or the fallback language if `language` is blank"
  [language]
  (if (s/blank? language)
    "de"
    language))

(defn- translate
  "Return a string for the given `key` and `locale`"
  [locale key]
  (get-in translations [locale key] key))

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
  (let [locale (keyword (fallback language))
        t (partial translate locale)]
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
  [{:keys [title creator subject description publisher date identifier source language rights 
           sourcedate sourceedition sourcepublisher sourcerights sourcetitle
           multimediatype multimediacontent narrator produceddate 
           revision revisiondate revisiondescription
           totaltime audioformat
           librarynumber]}] ; only books imported from the libray have this
  [:dtbook {:xmlns "http://www.daisy.org/z3986/2005/dtbook/"
            :version "2005-3" :xml:lang language}
   [:head
    [:meta {:name "dc:Title" :content title}]
    [:meta {:name "dc:Creator" :content creator}]
    [:meta {:name "dc:Subject" :content subject}]
    [:meta {:name "dc:Description" :content description}]
    [:meta {:name "dc:Publisher" :content publisher}]
    [:meta {:name "dc:Date" :content date}]
    [:meta {:name "dc:Type" :content "Text"}]
    [:meta {:name "dc:Format" :content "ANSI/NISO Z39.86-2005"}]
    [:meta {:name "dc:Identifier" :content identifier}]
    [:meta {:name "dc:Source" :content source}]
    [:meta {:name "dc:Language" :content language}]
    [:meta {:name "dc:Rights" :content rights}]
    [:meta {:name "dtb:uid" :content identifier}]
    [:meta {:name "dtb:sourceDate" :content sourcedate}]
    [:meta {:name "dtb:sourceEdition" :content sourceedition}]
    [:meta {:name "dtb:sourcePublisher" :content sourcepublisher}]
    [:meta {:name "dtb:sourceRights" :content sourcerights}]
    [:meta {:name "dtb:sourceTitle" :content sourcetitle}]
    [:meta {:name "dtb:multimediaType" :content multimediatype}]
    [:meta {:name "dtb:multimediaContent" :content multimediacontent}]
    [:meta {:name "dtb:narrator" :content narrator}]
    [:meta {:name "dtb:producer" :content publisher}]
    [:meta {:name "dtb:producedDate" :content produceddate}]
    [:meta {:name "dtb:revision" :content revision}]
    [:meta {:name "dtb:revisionDate" :content revisiondate}]
    [:meta {:name "dtb:revisionDescription" :content revisiondescription}]
    [:meta {:name "dtb:totalTime" :content totaltime}]
    [:meta {:name "dtb:audioFormat" :content audioformat}]]
   (if librarynumber
     (commercial-audiobook title creator language)
     (default-book title creator))])

(defn dtbook 
  "Create a minimal DTBook XML template according to [the spec](http://www.daisy.org/z3986/2005/Z3986-2005.html) for a given `production`"
  [production]
  (-> production
      dtbook-sexp
      xml/sexp-as-element
      xml/emit-str))
