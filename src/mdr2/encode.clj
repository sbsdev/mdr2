(ns mdr2.encode
  "Create DAISY Talking Books for recorded productions"
  (:require [clojure.java.io :refer [file]]
            [clojure.java.shell :refer [sh]]
            [me.raynes.fs :as fs]
            [mdr2.production :as production]
            [mdr2.pipeline1 :as pipeline]))

(defn encode-production
  "Encode a `production`, i.e. convert the wav files to mp3"
  [production]
  (let [output (production/encoded-path production)
        manifest (production/manifest-path production)]
    (fs/mkdir output)
    (pipeline/audio-encoder {:input manifest :output output})))

(defn create-iso
  "Pack a production in an iso file"
  [{:keys [title publisher]
    :or {title "FIXME:" publisher "FIXME:"} ; title and publisher shouldn't be empty
    :as production}]
  (let [encoded-path (production/encoded-path)
        iso-path (production/iso-path)]
    (fs/mkdir (fs/parent iso-path))
    (sh "genisoimage"
        "-quiet"
        "-r"
        "-publisher" publisher
        "-V" title ; volume ID (volume name or label)
        "-J" ; Generate Joliet directory records in addition to regular ISO9660 filenames.
        "-o" iso-path encoded-path)))

(defn fit-on-one-cd? [production]
  true)

(defn clean-up
  "Clean up temporary files of a production, namely the mp3 encoded
  DTB and the iso"
  [production]
  (fs/delete-dir (production/encoded-path production))
  (fs/delete-dir (fs/parent (production/iso-path production))))

(defn encode
  [production]
  ;; encode the production
  (let [encoded (encode-production production)]
    (if (fit-on-one-cd? encoded)
      (do
        ;; if it fits on one CD the create an iso
        (create-iso encoded)
        ;; and set the state to encoded
        (production/update! (assoc production :state :encoded)))
      ;; otherwise move the production to state :pending-volume-split
      ;; FIXME: clean up first
      (do
        (clean-up production)
        (production/update! (assoc production :state :pending-volume-split))))))
