(ns mdr2.dtbook
  "Functionality around [DTBook XML](http://www.daisy.org/z3986/2005/Z3986-2005.html)"
  (:require [clojure.java.io :refer [file]]
            [clojure.string :as s]
            [clojure.data.xml :as xml]
            [mdr2.production.path :as path]
            [mdr2.production :as prod]))

(xml/alias-uri 'dtb "http://www.daisy.org/z3986/2005/dtbook/")

;; FIXME: we should probably use some ready made library for i18n.
;; There is https://github.com/ptaoussanis/tower,
;; https://github.com/asmala/clj18n or
;; https://github.com/feldi/clojure-i18n. The first one seems like the
;; most mature but has a number of weird quirks and dependencies
(def ^:private translations
  {:de
   {:about "Zu diesem DAISY-Buch"
    :bib "Bibliographische Angaben"
    :blurb "Klappentexte"
    :about_the_book "Über den Inhalt"
    :about_the_author "Über den Verfasser/die Verfasserin"
    :contributors "Mitwirkende"
    :enclosures "Produktbeschreibung und Hörbuchbeilagen"
    :end "Ende des Buches"
    :title_creator_fmt "%s von %s"
    :unknown_title "Unbekannter Titel"}
   :en
   {:about "About this DAISY book"
    :bib "Bibliographic information"
    :blurb "Blurbs"
    :about_the_book "About the book"
    :about_the_author "About the author"
    :contributors "Contributors"
    :enclosures "Product description and Talking book enclosures"
    :end "End of book"
    :title_creator_fmt "%s by %s"
    :unknown_title "Unknown title"}})

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

(defn- default-book-title
  [title creator language]
  (let [t (partial translate language)
        present? (complement s/blank?)]
    (cond
      (and (present? creator) (present? title)) (format (t :title_creator_fmt) title creator)
      (present? title) title
      (present? creator) (format (t :title_creator_fmt) (t :unknown_title) creator)
      :else (t :unknown_title))))

(defn default-book
  "Return an default book sexp"
  [title creator language]
  [::dtb/book
   [::dtb/frontmatter
    [::dtb/doctitle (default-book-title title creator language)]
    [::dtb/docauthor creator]]
   [::dtb/bodymatter
    [::dtb/level1 [::dtb/h1] [::dtb/p]]]])

(defn periodical
  "Return an default periodical sexp"
  [title creator]
  [::dtb/book
   [::dtb/frontmatter
    [::dtb/doctitle title]
    [::dtb/docauthor creator]]
   [::dtb/bodymatter
    [::dtb/level1
     [::dtb/h1]
     [::dtb/p]]]])

(defn commercial-audiobook
  "Return an sexp for the body of a commercial audiobook"
  [title creator language]
  (let [t (partial translate language)]
    [::dtb/book
     [::dtb/frontmatter
      [::dtb/doctitle title]
      [::dtb/docauthor creator]
      [::dtb/level1
       [::dtb/h1 (t :about)]
       [::dtb/p]]
      [::dtb/level1
       [::dtb/h1 (t :bib)]
       [::dtb/p]]
      [::dtb/level1
       [::dtb/h1 (t :blurb)]
       [::dtb/p]]]
     [::dtb/bodymatter
      [::dtb/level1
       [::dtb/h1 "[Audioimport]"]
       [::dtb/p]]]
     [::dtb/rearmatter
      [::dtb/level1
       [::dtb/h1 (t :contributors)]
       [::dtb/p]]
      [::dtb/level1
       [::dtb/h1 (t :enclosures)]
       [::dtb/p]]
      [::dtb/level1
       [::dtb/h1 (t :end)]
       [::dtb/p]]]]))

(def conversions
  "Conversions to be applied to specific attributes of a production."
  {:total_time prod/smil-clock-value})

(defn- dtbook-sexp
  [{:keys [title creator language production_type library_number] :as production}]
  [::dtb/dtbook {:xmlns "http://www.daisy.org/z3986/2005/dtbook/"
                 :version "2005-3" :xml:lang language}
   [::dtb/head
    (for [[meta-name meta-key]
          (->> [:dc:Title :title
                :dc:Creator :creator
                :dc:Subject :subject
                :dc:Description :description
                :dc:Publisher :publisher
                :dc:Date :date
                :dc:Type :type
                :dc:Format :format
                :dc:Identifier :identifier
                :dc:Source :source
                :dc:Language :language
                :dc:Rights :rights
                :dtb:uid :identifier
                :dtb:sourceDate :source_date
                :dtb:sourceEdition :source_edition
                :dtb:sourcePublisher :source_publisher
                :dtb:sourceRights :source_rights
                :dtb:sourceTitle :source_title
                :dtb:multimediaType :multimedia_type
                :dtb:multimediaContent :multimedia_content
                :dtb:narrator :narrator
                :dtb:producer :publisher
                :dtb:producedDate :produced_date
                :dtb:revision :revision
                :dtb:revisionDate :revision_date
                :dtb:revisionDescription :revision_description
                :dtb:totalTime :total_time
                :dtb:audioFormat :audio_format]
               (partition 2))]
      [::dtb/meta {:name (name meta-name) :content ((get conversions meta-key meta-key) production)}])]
   (cond
     library_number (commercial-audiobook title creator language)
     (= production_type "periodical") (periodical title creator)
     :else (default-book title creator language))])

(defn dtbook
  "Create a minimal DTBook XML template according to [the spec](http://www.daisy.org/z3986/2005/Z3986-2005.html) for a given `production`"
  [production]
  (-> production
      dtbook-sexp
      xml/sexp-as-element
      (xml/emit-str :doctype doctype)))

(defn dtbook-file
  "Write a default dtbook file to the structured-path for the given
  `production`"
  [production]
  (let [file-name (str (:id production) ".xml")
        dtbook-file-name (file (path/structured-path production) file-name)]
    (spit dtbook-file-name (dtbook production))))
