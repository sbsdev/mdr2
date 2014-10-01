(ns mdr2.dtb
  "Functions to query [DAISY Talking Books](http://www.daisy.org/daisypedia/daisy-digital-talking-book)"
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :refer [file]]
            [clojure.string :as s])
  (:import javax.sound.sampled.AudioSystem
           java.nio.file.Files))

(defn content-type 
  "Return the content type for a given `file`"
  [file]
  (Files/probeContentType (.toPath file)))

(defn- wav-file?
  "Is the given `file` a wav file?"
  [file]
  (= (content-type file) "audio/x-wav"))

(defn- audio-file?
  "Is the given `file` an audio file, i.e. any of the valid audio file
  formats for a DAISY Talking Book namely *MPEG-4 AAC audio*, *MPEG-1/2
  Layer III (MP3) audio* and *Linear PCM - RIFF WAVE format audio*?"
  [file]
  (contains? #{"audio/mpeg" "audio/x-wav" "audio/mp4"} (content-type file)))

(defn- text-file?
  "Is the given `file` a text file, i.e. any of the valid text file
  formats for a DAISY Talking Book namely DTBook XML?"
  [file]
  (contains? #{"application/xml"} (content-type file)))

(defn- image-file?
  "Is the given `file` an image file, i.e. any of the valid image file
  formats for a DAISY Talking Book namely png and jpeg?"
  [file]
  (contains? #{"image/png" "image/jpeg"} (content-type file)))

(defn- ncx-file?
  "Is the given `file` an ncx file?"
  [file]
  (and (.isFile file) (.endsWith (.getName file) ".ncx")))

(defn- has-type? [type-pred path]
  (some type-pred (file-seq (file path))))

(defn- has-audio? [{path :path}] (has-type? audio-file? path))
(defn- has-text? [{path :path}] (has-type? text-file? path))
(defn- has-image? [{path :path}] (has-type? image-file? path))
(defn- has-ncx? [{path :path}] (has-type? ncx-file? path))

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
        has-ncx (has-ncx? dtb)]
    (cond 
     (and has-audio has-text) "audioFullText"
     (and has-audio has-ncx) "audioNCX"
     (and has-text has-ncx?) "textNCX"
     has-audio "audioOnly"
     :else "")))

(defn audio-format
  "Return the format in which the audio files in the DTB file set are
  written for a given DAISY Talking Book"
  [{path :path}]
  (let [audio-files (filter audio-file? (file-seq (file path)))]
    (cond 
     (every? #(contains? #{"audio/mp4"} (content-type %)) audio-files) "MP4-AAC"
     (every? #(contains? #{"audio/mpeg"} (content-type %)) audio-files) "MP3"
     (every? #(contains? #{"audio/x-wav"} (content-type %)) audio-files) "WAV"
     :else "")))

(defn- file-audio-length
  "Get the length of the audio in seconds for a given audio `file`"
  [file]
  ;; see http://stackoverflow.com/questions/3009908/how-do-i-get-a-sound-files-total-time-in-java
  (let [stream (AudioSystem/getAudioInputStream file)
        format (.getFormat stream)
        frameRate (.getFrameRate format)
        frames (.getFrameLength stream)
        durationInSeconds (/ frames frameRate)]
    durationInSeconds))

(defn audio-length
  "Return the audio legth for a given DAISY Talking Book in seconds"
  [{path :path}]
  ;; unfortunatelly getting the audio length of an mp3 file doesn't
  ;; seem to be supported at the moment. You need to have the proper
  ;; providers. Maybe this is a problem of openjdk? So just use wav
  ;; files for the calculation.
  (let [audio-files (filter wav-file? (file-seq (file path)))] 
    (reduce + (map file-audio-length audio-files))))

(defn meta-data
  "Return a map containing all queried meta data for a given DAISY Talking Book"
  [dtb]
  (let [keys [:multimedia_type :audio_format :audio_length]
        fns [multimedia-type audio-format audio-length]]
    ;; of course we could look up the fn using (ns-resolve ns (symbol
    ;; (name kw))) but there is a balance between readybility and
    ;; cleverness
    (zipmap keys (map #(%1 dtb) fns))))


