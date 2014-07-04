(ns mdr2.dtb
  "Functions to query DAISY Talking Books"
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :refer [file]])
  (:import javax.sound.sampled.AudioSystem))

(defn file-audio-length
  "Get the length of the audio in seconds for a given audio file"
  [file]
  ;; see http://stackoverflow.com/questions/3009908/how-do-i-get-a-sound-files-total-time-in-java
  (let [stream (AudioSystem/getAudioInputStream file)
        format (.getFormat stream)
        frameRate (.getFrameRate format)
        frames (.getFrameLength stream)
        durationInSeconds (/ frames frameRate)]
    durationInSeconds))

(defn wav-file? [file]
  (and (.isFile file)
       (.endsWith (.getName file) ".wav")))

(defn audio-length [dtb]
  (let [audio-files (filter wav-file? (file-seq (file dtb)))]
    (reduce + (map file-audio-length audio-files))))
