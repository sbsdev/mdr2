(ns mdr2.encode
  "Create DAISY Talking Books for recorded productions"
  (:require [clojure.java.io :refer [file]]
            [clojure.java.shell :refer [sh]]
            [clojure.tools.logging :as log]
            [clojure.string :as s]
            [me.raynes.fs :as fs]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.dtb :as dtb]
            [mdr2.dtb.xml :as xml]
            [mdr2.pipeline1 :as pipeline]
            [mdr2.pipeline2.scripts :as pipeline2]
            [mdr2.util :as util])
  (:import java.text.Normalizer
           java.text.Normalizer$Form))

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

(defn deaccent [str]
  "Remove accent from string"
  ;; https://gist.github.com/maio/e5f85d69c3f6ca281ccd
  (let [normalized (Normalizer/normalize str Normalizer$Form/NFD)]
    (s/replace normalized #"\p{InCombiningDiacriticalMarks}+" "")))

(defn truncate
  "Truncate and trim string `s` to length `n`"
  [s n]
  (s/trim (subs s 0 (min (count s) n))))

(defn create-iso
  "Pack a production in an iso file"
  [{:keys [title publisher]
    :or {title "FIXME:" publisher "FIXME:"} ; title and publisher shouldn't be empty
    :as production} & [volume]]
  (let [encoded-path (.getPath (path/encoded-path production volume))
        iso-path (path/iso-path production volume)
        iso-name (.getPath (path/iso-name production volume))
        clean-publisher (truncate (deaccent publisher) 128)
        clean-title (truncate (deaccent title) 32)]
    (log/debugf "Creating iso for %s (%s) with %s, %s, %s"
                (:id production) volume
                clean-publisher clean-title iso-name)
    (fs/mkdir iso-path)
    (sh "genisoimage"
        "-quiet"
        "-r"
        "-publisher" clean-publisher
        ;; volume ID (volume name or label). No more than 32
        ;; characters are allowed
        "-V" clean-title
        ;; Generate Joliet directory records in addition to regular
        ;; ISO9660 filenames
        "-J"
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
  (util/delete-directory! (path/encoded-path production))
  (util/delete-directory! (path/iso-path production)))

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
     ;; make sure the meta data of the recorded production aligns with
     ;; the meta data we have on file for this production
     (xml/update-meta-data! production volume)
     (let [{:keys [exit err]} (encode-production production bitrate volume sample-rate)]
       (if (not (and (= 0 exit) (s/blank? err)))
         ;; FIXME: This is very fishy: an error is basically logged
         ;; and ignored. The state is still set to encoded. Die hard
         ;; and with a bang!
         (log/errorf "Encoding of %s (%s) failed with exit %s and message \"%s\""
                     (:id production) volume exit err)
         (let [encoded_size (dtb/size (path/recorded-path production))]
           ;; add the encoded size to the meta data of the encoded production
           (xml/update-encoded-meta-data! (assoc production :encoded_size encoded_size) volume)
           ;; downgrade to daisy202
           (downgrade production)
           ;; create an iso
           (let [{:keys [exit err]} (create-iso production volume)]
             (when (not (and (= 0 exit) (s/blank? err)))
               ;; FIXME: Again this is very fishy: an error is basically
               ;; logged and ignored. The state is still set to encoded. Die
               ;; hard and with a bang!
               (log/errorf "Generating iso for %s (%s) failed with exit %s and message \"%s\""
                           (:id production) volume exit err)))))))
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

