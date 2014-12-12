(ns mdr2.encode
  "Create DAISY Talking Books for recorded productions"
  (:require [clojure.java.io :refer [file]]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.dtb :as dtb]
            [mdr2.pipeline1 :as pipeline]
            [mdr2.pipeline2.scripts :as pipeline2]))

;; according to wikipedia it should be 737280000 (see
;; http://en.wikipedia.org/wiki/CD-ROM#Capacity) but according to k3b
;; it is 666.4 MiB (2,048 B * 341,186 blocks = 698,748,928 B)
(def ^:private max-size
  "Max number of bytes that fit on a CD-ROM"
  698748928)

(def sampling-rates
  "Possible sample rates for encoding a DTB"
  {:mono 22050 :stereo 44100})

(defn sampling-rate
  "Return the sampling rate that should be used for the given
  `production`. By default 22050.0 is used but for stereo productions
  we use 44100.0"
  [production]
  (let [dtb (path/recorded-path production)
        channels (dtb/audio-channels dtb)]
    (if (= channels 1)
      (:mono sampling-rates)
      (:stereo sampling-rates))))

(defn encode-production
  "Encode a `production` using the given `bitrate`, i.e. convert the
  wav files to mp3"
  [production bitrate]
  (let [output (path/encoded-path production)
        manifest (path/manifest-path production)
        stereo (if (dtb/mono? (path/recorded-path production)) "mono" "stereo")
        sampling-rate (sampling-rate production)]
    (fs/mkdir output)
    (pipeline/audio-encoder (.getPath manifest) (.getPath output)
                            :bitrate bitrate :stereo stereo :freq sampling-rate)))

(defn create-iso
  "Pack a production in an iso file"
  [{:keys [title publisher]
    :or {title "FIXME:" publisher "FIXME:"} ; title and publisher shouldn't be empty
    :as production}]
  (let [encoded-path (.getPath (path/encoded-path production))
        iso-path (path/iso-path production)
        iso-name (.getPath (path/iso-name production))]
    (fs/mkdir iso-path)
    (sh "genisoimage"
        "-quiet"
        "-r"
        "-publisher" publisher
        "-V" title ; volume ID (volume name or label)
        "-J" ; Generate Joliet directory records in addition to regular ISO9660 filenames.
        "-o" iso-name encoded-path)))

(def bitrates
  "Possible bitrates for encoding a DTB"
  [128 64 56 48])

(defn ideal-bitrate
  "Calculate the ideal bitrate based on the size of a production and
  how much will generally fit on a CD-ROM. It will first try a higher
  bitrate (see `bitrates`). If the production still doesn't fit on one
  CD it will subsequently try lesser bitrates. Return nil if the
  content doesn't even fit on one CD with the lowest bitrate."
  [production]
  (let [dtb (path/recorded-path production)
        duration (dtb/audio-length dtb)
        sampling-ratio (if (dtb/mono? dtb) 1/2 1)
        max-bitrate (/ (* (/ max-size duration sampling-ratio) 8) 1000)]
    (loop [bitrates bitrates]
      (when-first [bitrate bitrates]
        (if (> max-bitrate bitrate)
          bitrate
          (recur (rest bitrates)))))))

(defn clean-up
  "Clean up temporary files of a production, namely the mp3 encoded
  DTB and the iso"
  [production]
  (fs/delete-dir (path/encoded-path production))
  (fs/delete-dir (path/iso-path production)))

(defn downgrade
  [production]
  (pipeline2/daisy3-to-daisy202))

(defn encode
  [production]
  (if-let [bitrate (ideal-bitrate production)]
    (do
      ;; encode the production
      (let [result (encode-production production bitrate)]
        (if (not= 0 (:exit result))
          (log/errorf "Encoding failed with %s" (:err result))
          (do
            ;; downgrade to daisy202
            (downgrade production)
            ;; create an iso
            (create-iso production)
            ;; set the state to encoded
            (prod/update! (assoc production :state "encoded"))))))
    ;; otherwise move the production to state :pending-volume-split
    (prod/update! (assoc production :state "pending-split"))))
