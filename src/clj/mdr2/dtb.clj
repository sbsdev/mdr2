(ns mdr2.dtb
  "Functions to query [DAISY Talking
  Books](http://www.daisy.org/daisypedia/daisy-digital-talking-book)"
  (:require [clojure.java.io :refer [file]]
            [clojure.string :as s]
            [clojure.tools.logging :as log]
            [clojure.math :as math]
            [pantomime.mime :refer [mime-type-of]]
            [mdr2.production.path :as path]
            [clojure.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml1-> attr= attr]])
  (:import javax.sound.sampled.AudioSystem
           java.lang.NumberFormatException))

(defn wav-file?
  "Is the given `file` a wav file?"
  [file]
  (= (mime-type-of file) "audio/x-wav"))

(defn- audio-file?
  "Is the given `file` an audio file, i.e. any of the valid audio file
  formats for a DAISY Talking Book namely *MPEG-4 AAC audio*, *MPEG-1/2
  Layer III (MP3) audio* and *Linear PCM - RIFF WAVE format audio*?"
  [file]
  (#{"audio/mpeg" "audio/x-wav" "audio/mp4"} (mime-type-of file)))

(defn- text-file?
  "Is the given `file` a text file, i.e. any of the valid text file
  formats for a DAISY Talking Book namely DTBook XML?"
  [file]
  (#{"application/xml"} (mime-type-of file)))

(defn- image-file?
  "Is the given `file` an image file, i.e. any of the valid image file
  formats for a DAISY Talking Book namely png and jpeg?"
  [file]
  (#{"image/png" "image/jpeg"} (mime-type-of file)))

(defn- ncx-file?
  "Is the given `file` an ncx file?"
  [file]
  (and (.isFile file) (.endsWith (.getName file) ".ncx")))

(defn- ncc-file?
  "Is the given `file` an ncc file?"
  [file]
  (and (.isFile file) (= (.getName file) "ncc.html")))

(defn- get-files
  "Return a seq of all regular files contained in `dtb`"
  [dtb]
  (->> dtb file file-seq (filter #(.isFile %))))

(defn- has-type? [type-pred path]
  (some type-pred (get-files path)))

(defn- has-audio? [dtb] (has-type? audio-file? dtb))
(defn- has-text? [dtb] (has-type? text-file? dtb))
(defn- has-image? [dtb] (has-type? image-file? dtb))
(defn- has-ncx? [dtb] (has-type? ncx-file? dtb))
(defn- has-ncc? [dtb] (has-type? ncc-file? dtb))

(defn- audio-content [dtb] (when (has-audio? dtb) "audio"))
(defn- text-content [dtb] (when (has-text? dtb) "text"))
(defn- image-content [dtb] (when (has-image? dtb) "image"))

(defn multimedia-content
  "Return the multimedia content for a given DAISY Talking Book"
  [dtb]
  (->> [audio-content text-content image-content]
       (map #(%1 dtb))
       (remove nil?)
       (s/join ",")))

(defn multimedia-type
  "Return the multimedia type for a given DAISY Talking Book"
  [dtb]
  (let [has-audio (has-audio? dtb)
        has-text (has-text? dtb)
        has-ncx (has-ncx? dtb)
        has-ncc (has-ncc? dtb)]
    (cond 
     (and has-audio has-text (or has-ncx has-ncc)) "audioFullText"
     (and has-audio has-ncx) "audioNCX"
     (and has-audio has-ncc) "audioNcc"
     (and has-text has-ncx?) "textNCX"
     (and has-text has-ncc) "textNcc"
     has-audio "audioOnly"
     :else "")))

(defn audio-format
  "Return the format in which the audio files in the DTB file set are
  written for a given DAISY Talking Book"
  [dtb]
  (let [mime-types (->> dtb
                        get-files
                        (filter audio-file?)
                        (remove #(= (.getName %) "tpbnarrator_res.mp3"))
                        (map mime-type-of))]
    (cond
     (every? #{"audio/mp4"} mime-types) "MP4-AAC"
     (every? #{"audio/mpeg"} mime-types) "MP3"
     (every? #{"audio/x-wav"} mime-types) "WAV"
     :else "")))

(defn- file-audio-length
  "Get the length of the audio in seconds for a given audio `file`"
  [file]
  ;; see http://stackoverflow.com/questions/3009908/how-do-i-get-a-sound-files-total-time-in-java
  (with-open [stream (AudioSystem/getAudioInputStream file)]
    (let [format (.getFormat stream)
          frameRate (.getFrameRate format)
          frames (.getFrameLength stream)
          durationInSeconds (/ frames frameRate)]
      durationInSeconds)))

(defn audio-length
  "Return the audio length for a given DAISY Talking Book in
  milliseconds"
  [dtb]
  ;; unfortunatelly getting the audio length of an mp3 file doesn't
  ;; seem to be supported at the moment. You need to have the proper
  ;; providers. Maybe this is a problem of openjdk? So just use wav
  ;; files for the calculation.
  (let [audio-files (filter wav-file? (get-files dtb))]
    (->> audio-files
     (map file-audio-length)
     (reduce +)
     (* 1000)
     math/round)))

(defn- file-audio-channels
  "Return the number of audio channels for a given audio `file`"
  [file]
  (with-open [stream (AudioSystem/getAudioInputStream file)]
    (let [format (.getFormat stream)]
      (.getChannels format))))

(defn audio-channels
  "Return the number of audio channels for a given DAISY Talking Book"
  [dtb]
  (let [audio-files (filter wav-file? (get-files dtb))
        channels (map file-audio-channels audio-files)]
    ;; if not all files are mono we assume the whole book is stereo
    (if (every? #(= 1 %) channels) 1 2)))

(defn mono?
  "Return true if a DAISY Talking Book is mono"
  [dtb]
  (= (audio-channels dtb) 1))

(defn file-sampling-rate
  "Return the sample rate for a given audio `file`"
  [file]
  (with-open [stream (AudioSystem/getAudioInputStream file)]
    (let [format (.getFormat stream)]
      (.getSampleRate format))))

(defn sampling-rate
  "Return the sample rate for a given DAISY Talking Book. If there is
  a mix of sampling rates used the most frequently used is returned"
  [dtb]
  (let [audio-files (filter wav-file? (get-files dtb))
        sampling-rates (map file-sampling-rate audio-files)]
    (ffirst (sort-by val (frequencies sampling-rates)))))

(defn size
  "Return the size in kBytes of a given DAISY Talking Book"
  [dtb]
  (let [files (get-files dtb)
        bytes (->> files (map #(.length %)) (reduce +))]
    (-> bytes (/ 1024) math/round)))

(defn depth
  "Return the depth of a given DAISY Talking Book.

  Unlike the other functions which mostly do not trust the meta data
  from the DTB and calculate the data based on the wav files, the
  depth is simply taken out of the meta data from the manifest."
  [dtb]
  (let [manifest (file dtb path/manifest-member)
        zipper (-> manifest xml/parse zip/xml-zip)
        depth-str (xml1-> zipper :head :meta (attr= :name "ncc:depth") (attr :content))]
    (try
      (Integer/valueOf depth-str)
      ;; if there is no sensible depth log this and just return a default of 1
      (catch NumberFormatException _
        (log/warnf "%s contains invalid depth \"%s\". Using 1 as a fallback" manifest depth-str)
        1))))

(defn meta-data
  "Return a map containing all queried meta data for a given DAISY Talking Book"
  [dtb]
  (let [keys [:multimedia_type :audio_format :total_time :depth]
        fns [multimedia-type audio-format audio-length depth]]
    (zipmap keys (map #(% dtb) fns))))

