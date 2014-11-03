(ns mdr2.encode
  "Create DAISY Talking Books for recorded productions"
  (:require [clojure.java.io :refer [file]]
            [clojure.java.shell :refer [sh]]
            [me.raynes.fs :as fs]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.pipeline1 :as pipeline]))

(defn encode-production
  "Encode a `production`, i.e. convert the wav files to mp3"
  [production]
  (let [output (path/encoded-path production)
        manifest (path/manifest-path production)]
    (fs/mkdir output)
    (pipeline/audio-encoder {:input manifest :output output})))

(defn create-iso
  "Pack a production in an iso file"
  [{:keys [title publisher]
    :or {title "FIXME:" publisher "FIXME:"} ; title and publisher shouldn't be empty
    :as production}]
  (let [encoded-path (path/encoded-path production)
        iso-path (path/iso-path production)
        iso-name (path/iso-name production)]
    (fs/mkdir iso-path)
    (sh "genisoimage"
        "-quiet"
        "-r"
        "-publisher" publisher
        "-V" title ; volume ID (volume name or label)
        "-J" ; Generate Joliet directory records in addition to regular ISO9660 filenames.
        "-o" iso-name encoded-path)))

;; according to wikipedia it should be 737280000 (see
;; http://en.wikipedia.org/wiki/CD-ROM#Capacity) but according to k3b
;; it is 666.4 MiB (2,048 B * 341,186 blocks = 698,748,928 B)
(def ^:private max-size
  "Max number of bytes that fit on a CD-ROM"
  698748928)

(defn fit-on-one-cd?
  "Return true if the `production`, i.e. the resulting iso file fits
  on one CD"
  [production]
  (let [path (path/iso-path production)
        size (fs/size path)]
    (< size max-size)))

(defn clean-up
  "Clean up temporary files of a production, namely the mp3 encoded
  DTB and the iso"
  [production]
  (fs/delete-dir (path/encoded-path production))
  (fs/delete-dir (path/iso-path production)))

(defn encode
  [production]
  ;; encode the production
  (encode-production production)
  ;; create an iso
  (create-iso production)
  (if (fit-on-one-cd? production)
    ;; set the state to encoded
    (prod/update! (assoc production :state :encoded))
    ;; otherwise move the production to state :pending-volume-split
    ;; FIXME: clean up first
    (do
      (clean-up production)
      (prod/update! (assoc production :state :pending-volume-split)))))
