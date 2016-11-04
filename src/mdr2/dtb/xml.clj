(ns mdr2.dtb.xml
  "Update meta data of a [DAISY Talking
  Book](http://www.daisy.org/daisypedia/daisy-digital-talking-book)"
  (:require [clojure
             [string :as str]
             [walk :as walk]]
            [clojure.data.xml :as xml]
            [clojure.java
             [io :as io]
             [shell :as shell]]
            [clojure.tools.logging :as log]
            [mdr2.data.xml :as xml-new]
            [mdr2.production.path :as path]
            [org.tobereplaced.nio.file :as nio])
  (:import java.nio.file.StandardCopyOption))

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

(defn meta-node? [node name]
  (and (= (:tag node) :meta)
       (= (:name (:attrs node)) name)))

(defn update-meta-node [node v]
  (assoc-in node [:attrs :content] v))

(defn title-node? [node] (= (:tag node) :title))

(defn update-title-node [node v] (assoc-in node [:content] (list v)))

(defn insert-kbytesize-node [node value]
  "Append a ncc:kByteSize meta element to `node` if the given `value`
  is not nil"
  (if value
    (update-in node [:content] concat ; append
               (list (xml/element :meta{:name "ncc:kByteSize" :content value})))
    node))

(defn insert-xmlns [node]
  (update-in node [:attrs] assoc :xmlns "http://www.w3.org/1999/xhtml"))

(defn handle-manifest-node
  "Handle one node in the xml tree. Replace all content in the meta
  elements with the values from `production`"
  [node {:keys [creator title date identifier language publisher
                source type narrator produced_date producer
                revision_date source_edition source_publisher
                source_date multimedia_type encoded_size]
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
    (meta-node? node "dc:date") (update-meta-node node date)
    (meta-node? node "dc:identifier") (update-meta-node node identifier)
    (meta-node? node "dc:language") (update-meta-node node language)
    (meta-node? node "dc:publisher") (update-meta-node node publisher)
    (meta-node? node "dc:source") (update-meta-node node source)
    (meta-node? node "dc:type") (update-meta-node node type)
    (meta-node? node "ncc:narrator") (update-meta-node node narrator)
    (meta-node? node "ncc:producedDate") (update-meta-node node produced_date)
    (meta-node? node "ncc:sourceDate") (update-meta-node node source_date)
    (meta-node? node "ncc:producer") (update-meta-node node producer)
    (meta-node? node "ncc:revisionDate") (update-meta-node node revision_date)
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
  (walk/postwalk #(handler % production) xml))

(defn xml-format!
  "Format xml file `in` and store it in file `out`"
  [in out]
  (let [{:keys [exit err]}
        (shell/sh "xmllint" "--format" "--nonet"
                  "--output" (.getAbsolutePath out)
                  (.getAbsolutePath in))]
    (when-not (and (= 0 exit) (str/blank? err))
      (log/errorf "xmllint of %s failed with exit %s and message \"%s\""
                  (.getAbsolutePath in) exit (str/trim-newline err)))))

(defn update-mainfest!
  "Update the manifest file of `volume` for `production` in-place with
  the meta data from `production`. If a `manifest` is given the
  `volume` is ignored"
  ([production volume]
   (update-mainfest! production volume (path/manifest-path production volume)))
  ([production _ manifest]
   (let [updated (with-open [r (io/reader manifest)]
                  (update-meta-data (xml/parse r :support-dtd false)
                                    production handle-manifest-node))]
     (let [tmp-file (io/file (nio/create-temp-file! "mdr2-" ".xml"))]
       (with-open [w (io/writer tmp-file)]
         (xml-new/emit updated w :doctype manifest-doctype))
       (xml-format! tmp-file manifest)
       (nio/delete! tmp-file)))))

(defn update-master-smil!
  "Update the master smil file of `volume` for `production` in-place
  with the meta data from `production`"
  [production volume]
  (let [smil (io/file (path/recorded-path production volume) "master.smil")
        updated (with-open [r (io/reader smil)]
                  (update-meta-data
                   (xml/parse r :support-dtd false)
                   production handle-smil-node))]
    (let [tmp-file (io/file (nio/create-temp-file! "mdr2-" ".smil"))]
      (with-open [w (io/writer tmp-file)]
        (xml-new/emit updated w :doctype smil-doctype))
      (xml-format! tmp-file smil)
      (nio/delete! tmp-file))))

(defn format-smil-files!
  "Format all smil files. OBI now produces unformated smil files and
  some old players don't seem to like this"
  [production volume]
  (let [formatted (io/file "/tmp/formatted.smil")
        dir (io/file (path/recorded-path production volume))
        files (->> dir
                   file-seq
                   (filter #(str/ends-with? (.getName %) ".smil")))]
    (doseq [file files]
      (xml-format! file formatted)
      (nio/move! formatted file StandardCopyOption/REPLACE_EXISTING))))

(defn update-meta-data!
  "Update the manifest and the smil file of a `volume` for
  `production` in-place with the meta data from `production`"
  [production volume]
  (update-mainfest! production volume)
  (update-master-smil! production volume))

(defn update-encoded-meta-data!
  "Update the meta data of an encoded `volume` for `production`
  in-place. This can be useful in situations where the meta data is
  only known after the encoding, such as `:encoded_size`. Presumably
  only the manifest will be affected. The smil files will not be
  touched"
  [production volume]
  (update-mainfest! production volume
   (io/file (path/encoded-path production volume) path/manifest-member)))
