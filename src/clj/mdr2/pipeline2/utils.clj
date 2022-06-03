(ns mdr2.pipeline2.utils
  "Utilities for dealing with the [Pipeline2 Web Service
  API](https://code.google.com/p/daisy-pipeline/wiki/WebServiceAPI)"
  (:require [clojure.zip :refer [xml-zip]]
            [clojure.data.zip.xml :refer [xml1-> attr]]
            [mdr2.pipeline2.core :as dp2]))

(def ^:private poll-interval 1000)

(defn- get-id [job]
  (-> job xml-zip (xml1-> (attr :id))))

(defn- get-status [job]
  (-> job xml-zip (xml1-> (attr :status))))

(defn create-job-and-wait [script inputs options]
  (let [id (get-id (dp2/job-create script inputs options))]
    (Thread/sleep poll-interval) ; wait a bit before polling the first time
    (loop [result (dp2/job id)]
      (let [status (get-status result)]
        (if (= "RUNNING" status)
          (do
            (Thread/sleep poll-interval)
            (recur (dp2/job id)))
          result)))))
