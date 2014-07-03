(ns mdr2.dtb
  "Functions to query DAISY Talking Books"
  (:require [clojure.data.xml :as xml]
            [clojure.java.io :refer [file]])
  (:import javax.sound.sampled.AudioSystem))

(defn file-audio-length [file]
  (let [stream (AudioSystem/getAudioInputStream file)
        format (.getFormat stream)
        frameRate (.getFrameRate format)
        frames (.getFrameLength stream)
        durationInSeconds (/ frames frameRate)]
    durationInSeconds))

(defn audio-file? [file]
  (and (.isFile file)
       (.endsWith ".wav" (.getName file))))

(defn audio-length [dtb]
  (let [audio-files (filter audio-file? (file-seq (file dtb)))]
    (reduce + (map file-audio-length audio-files))))

  

