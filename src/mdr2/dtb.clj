(ns mdr2.dtb
  "Functions to query [DAISY Talking Books](http://www.daisy.org/daisypedia/daisy-digital-talking-book)"
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :refer [file]])
  (:import javax.sound.sampled.AudioSystem))

(defn depth
  "Return the depth for a given DAISY Talking Book"
  [dtb] 0)

(defn files
  "Return the number of files for a given DAISY Talking Book"
  [dtb] 0)

(defn size
  "Return the size for a given DAISY Talking Book in kBytes"
  [dtb] 0)

(defn page-front
  "Return the number of front pages for a given DAISY Talking Book"
  [dtb] 0)

(defn page-normal
  "Return the number of normal pages for a given DAISY Talking Book"
  [dtb] 0)

(defn page-special
  "Return the number of special pages for a given DAISY Talking Book"
  [dtb] 0)

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

(defn- wav-file?
  "Is the given `file` a wav file?"
  [file]
  (and (.isFile file)
       (.endsWith (.getName file) ".wav")))

(defn audio-length
  "Return the audio legth for a given DAISY Talking Book in seconds"
  [dtb]
  (let [audio-files (filter wav-file? (file-seq (file dtb)))]
    (reduce + (map file-audio-length audio-files))))

(defn meta-data
  "Return a map containing all queried meta data for a given DAISY Talking Book"
  [dtb]
  (let [keys [:depth :files :size :page-front :page-normal :page-special :audio-length]
        fns [depth files size page-front page-normal page-special audio-length]]
    ;; of course we could look up the fn using (ns-resolve ns (symbol
    ;; (name kw))) but there is a balance between readybility and
    ;; cleverness
    (zipmap keys (map #(%1 dtb) fns))))


