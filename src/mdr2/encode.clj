(ns mdr2.encode
  "Create DAISY Talking Books for recorded productions"
  (:require [clojure.java.io :refer [file]]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.logging :as log]
            [me.raynes.fs :as fs]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.dtb :as dtb]
            [mdr2.dtb.xml :as xml]
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
  "Encode a `production` using the given `bitrate`, `volume` and
  `sample-rate`, i.e. convert the wav files to mp3"
  [production bitrate volume sample-rate]
  (let [output (path/encoded-path production volume)
        manifest (path/manifest-path production volume)
        stereo (if (dtb/mono? (path/recorded-path production volume)) "mono" "stereo")]
    (fs/mkdir output)
    (log/infof "Encoding %s (%s) with bitrate: %s" (:id production) volume bitrate)
    (log/debugf "Encoding %s (%s) with %s, %s, %s, %s, %s" (:id production) volume bitrate manifest output stereo sample-rate)
    (pipeline/audio-encoder (.getPath manifest) (.getPath output)
                            :bitrate bitrate :stereo stereo :freq sample-rate)))

(defn create-iso
  "Pack a production in an iso file"
  [{:keys [title publisher]
    :or {title "FIXME:" publisher "FIXME:"} ; title and publisher shouldn't be empty
    :as production} & [volume]]
  (let [encoded-path (.getPath (path/encoded-path production volume))
        iso-path (path/iso-path production volume)
        iso-name (.getPath (path/iso-name production volume))]
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
  CD it will subsequently try lesser bitrates. Return 0 if the content
  doesn't even fit on one CD with the lowest bitrate."
  [production]
  (let [dtb (path/recorded-path production)
        duration (quot (dtb/audio-length dtb) 1000) ; convert from millisecs to secs
        sampling-ratio (if (dtb/mono? dtb) 1 2)
        max-bitrate (/ (* (/ max-size duration sampling-ratio) 8) 1000)]
    (->> bitrates
         (filter #(<= % max-bitrate))
         (apply max 0))))

(defn clean-up
  "Clean up temporary files of a production, namely the mp3 encoded
  DTB and the iso"
  [production]
  (fs/delete-dir (path/encoded-path production))
  (fs/delete-dir (path/iso-path production)))

(defn downgrade
  [production]
  ;; FIXME: Implement properly
  ;;(pipeline2/daisy3-to-daisy202 (path/encoded-path) downgraded-path)
)

(defn encode
  "Encode a `production` with the given `bitrate` and optional
  `sample-rate`"
  ([production bitrate]
   (encode production bitrate (sampling-rate production)))
  ([production bitrate sample-rate]
   (doseq [volume (range 1 (inc (:volumes production)))]
     (let [result (encode-production production bitrate volume sample-rate)]
       (if (not= 0 (:exit result))
         (log/errorf "Encoding failed with %s" (:err result))
         (do
           ;; update meta data
           (xml/update-meta-data! production)
           ;; downgrade to daisy202
           (downgrade production)
           ;; create an iso
           (create-iso production volume)))))
   (prod/set-state-encoded! production)))

(defn encode-or-split
  "Encode a `production` if it either fits on one volume or it has
  been split into multiple volumes already. Otherwise forward it to
  manual splitting. If `bitrate` and `sample-rate` are given it is
  expected that the production is in state \"split\""
  ([{:keys [volumes state] :as production}]
   {:pre [(= state "recorded")]}
   (let [ideal-bitrate (ideal-bitrate production)]
     (if (and (= volumes 1) (not= ideal-bitrate 0))
       ;; the production has just been recorded, no specific number
       ;; of volumes are required and it fits on one volume
       (encode production ideal-bitrate)
       ;; if the production doesn't fit one one volume or a specific
       ;; number of volumes is requested forward to manual split
       (prod/set-state! production "pending-split"))))
  ([{:keys [state] :as production} bitrate sample-rate]
   {:pre [(= state "split")]}
   (encode production bitrate sample-rate)))

