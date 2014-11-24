(ns mdr2.dtb
  "Functions to query [DAISY Talking
  Books](http://www.daisy.org/daisypedia/daisy-digital-talking-book)"
  (:require [clojure.java.io :refer [file]]
            [clojure.string :as s]
            [pantomime.mime :refer [mime-type-of]])
  (:import javax.sound.sampled.AudioSystem))

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

(defn- has-type? [type-pred path]
  (some type-pred (file-seq (file path))))

(defn- has-audio? [dtb] (has-type? audio-file? dtb))
(defn- has-text? [dtb] (has-type? text-file? dtb))
(defn- has-image? [dtb] (has-type? image-file? dtb))
(defn- has-ncx? [dtb] (has-type? ncx-file? dtb))

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
  [dtb]
  (let [mime-types (->> (file-seq (file dtb))
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
  (let [stream (AudioSystem/getAudioInputStream file)
        format (.getFormat stream)
        frameRate (.getFrameRate format)
        frames (.getFrameLength stream)
        durationInSeconds (/ frames frameRate)]
    durationInSeconds))

(defn audio-length
  "Return the audio legth for a given DAISY Talking Book in seconds"
  [dtb]
  ;; unfortunatelly getting the audio length of an mp3 file doesn't
  ;; seem to be supported at the moment. You need to have the proper
  ;; providers. Maybe this is a problem of openjdk? So just use wav
  ;; files for the calculation.
  (let [audio-files (filter wav-file? (file-seq (file dtb)))] 
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


