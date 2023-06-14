(ns mdr2.pipeline2.scripts
  "Thin layer above the [Pipeline2 Web Service
  API](https://code.google.com/p/daisy-pipeline/wiki/WebServiceAPI) to
  invoke specific scripts."
  (:require
   [babashka.fs :as fs]
   [mdr2.pipeline2.core :as pipeline2]))

(defn validate [input & {:keys [mathml-version check-images] :as opts}]
  (pipeline2/create-job-and-wait "dtbook-validator" {} (merge opts {:input-dtbook input})))

(defn daisy3-to-epub3 [input & {:keys [mediaoverlays assert-valid] :as opts}]
  (pipeline2/create-job-and-wait "daisy3-to-epub3" {:source input} opts))

(defn epub3-to-daisy202 [input & {:keys [temp-dir output-dir] :as opts}]
  (pipeline2/create-job-and-wait "epub3-to-daisy202" {} (merge {:epub input} opts)))

(defn daisy3-to-daisy202 [input output-dir]
  (let [epub-dir (fs/create-temp-dir "mdr2")]
    (daisy3-to-epub3 input :mediaoverlays true :assert-valid true :output-dir epub-dir)
    (epub3-to-daisy202 epub-dir :output-dir output-dir)))
