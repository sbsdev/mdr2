(ns mdr2.dtb.xml
  (:require [clojure.java.io :as io]
            [clojure.data.xml :as xml]
            [mdr2.data.xml :as xml-new]
            [clojure.walk :as w]
            [clojure.zip :as zip]
            [clj-time.format :as f]
            [clj-time.coerce :refer [from-date]]
            [mdr2.production.path :as path]))

(def ^:private manifest-doctype
  (str
   "<!DOCTYPE html PUBLIC "
   "\"-//W3C//DTD XHTML 1.0 Transitional//EN\" "
   "\"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">"))

(def ^:private smil-doctype
  (str
   "<!DOCTYPE smil PUBLIC "
   "\"-//W3C//DTD SMIL 1.0//EN\" "
   "\"http://www.w3.org/TR/REC-smil/SMIL10.dtd\">"))

(def ^:private formatter (f/formatters :date))
(defn- format-date [date] (when date (f/unparse formatter (from-date date))))

(defn meta-node? [node name]
  (and (= (:tag node) :meta)
       (= (:name (:attrs node)) name)))

(defn update-meta-node [node v]
  (assoc-in node [:attrs :content] v))

(defn title-node? [node] (= (:tag node) :title))

(defn update-title-node [node v] (assoc-in node [:content] (list v)))

(defn insert-kbytesize-node [node v]
  (update-in node [:content] concat ; append
             (list (xml/element :meta {:name "ncc:kByteSize" :content v}))))

(defn insert-xmlns [node]
  (update-in node [:attrs] assoc :xmlns "http://www.w3.org/1999/xhtml"))

(defn handle-manifest-node
  "Handle one node in the xml tree. Replace all content in the meta
  elements with the values from `production`"
  [node {:keys [creator title date identifier language publisher
                source type narrator produced_date producer
                revision_date source_edition source_publisher
                multimedia_type encoded_size]
         :as production}]
  (cond
    ;; FIXME: this is a gross hack using one bug to work around
    ;; another one. Once clojure properly handles xml namespaces this
    ;; will probably not work anymore and should no longer be needed.
    ;; The basic problem is the the default xml namespace is not
    ;; loaded, so emit will die. So we just rename the namespaced
    ;; xml:lang attribute into a not namespaced
    (= node :xml/lang) :xml:lang
    (title-node? node) (update-title-node node title)
    (meta-node? node "dc:creator") (update-meta-node node creator)
    (meta-node? node "dc:title") (update-meta-node node title)
    (meta-node? node "dc:date") (update-meta-node node (format-date date))
    (meta-node? node "dc:identifier") (update-meta-node node identifier)
    (meta-node? node "dc:language") (update-meta-node node language)
    (meta-node? node "dc:publisher") (update-meta-node node publisher)
    (meta-node? node "dc:source") (update-meta-node node source)
    (meta-node? node "dc:type") (update-meta-node node type)
    (meta-node? node "ncc:narrator") (update-meta-node node narrator)
    (meta-node? node "ncc:producedDate") (update-meta-node node (format-date produced_date))
    (meta-node? node "ncc:producer") (update-meta-node node producer)
    (meta-node? node "ncc:revisionDate") (update-meta-node node (format-date revision_date))
    (meta-node? node "ncc:sourceEdition") (update-meta-node node source_edition)
    (meta-node? node "ncc:sourcePublisher") (update-meta-node node source_publisher)
    (meta-node? node "ncc:multimediaType") (update-meta-node node multimedia_type)
    (= (:tag node) :head) (insert-kbytesize-node node encoded_size)
    ;; FIXME: xml/parse seems to drop the xmlns attribute. We have to fudge it back in
    (= (:tag node) :html) (insert-xmlns node)
    :else node))

(defn handle-smil-node
  [node {:keys [title identifier]}]
  (cond
    (meta-node? node "dc:title") (update-meta-node node title)
    (meta-node? node "dc:identifier") (update-meta-node node identifier)
    :else node))

(defn update-meta-data
  "Update `xml` with the metadata from `production` using the given
  node `handler`"
  [xml production handler]
  (w/postwalk #(handler % production) xml))

(defn update-mainfest!
  "Update the manifest and the smil file of a `production` inplace
  with the meta data from `production`"
  [production]
  (let [manifest (path/manifest-path production)
        updated (with-open [r (io/reader manifest)]
                  (update-meta-data (xml/parse r :support-dtd false)
                                    production handle-manifest-node))]
    (with-open [w (io/writer manifest)]
      (xml-new/emit updated w :doctype manifest-doctype))))

(defn update-master-smil!
  [production]
  (let [smil (io/file (path/recorded-path production) "master.smil")
        updated (with-open [r (io/reader smil)]
                  (update-meta-data
                   (xml/parse r :support-dtd false)
                   production handle-smil-node))]
    (with-open [w (io/writer smil)]
      (xml-new/emit updated w :doctype smil-doctype))))

(defn update-meta-data!
  "Update the manifest and the smil file of a `production` inplace
  with the meta data from `production`"
  [production]
  (update-mainfest! production)
  (update-master-smil! production))
