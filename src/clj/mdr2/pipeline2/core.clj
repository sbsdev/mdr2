(ns mdr2.pipeline2.core
  "Thin layer on top of the [Pipeline2 Web Service
  API](https://code.google.com/p/daisy-pipeline/wiki/WebServiceAPI)"
  (:require [clojure.data.xml :as xml]
            [java-time :as time]
            [pandect.core :as pandect]
            [clojure.data.codec.base64 :as b64]
            [crypto.random :as crypt-rand]
            [clj-http.client :as client]
            [clj-http.util :refer [url-encode]]))

(def ws-url "http://localhost:8181/ws")

(def ^:private auth-id "clientid")
(def ^:private secret "supersekret")
(def ^:private remote false)

(defn- create-hash [message signing-key]
  (-> (pandect/sha1-hmac-bytes message signing-key)
      b64/encode
      String.))

(defn auth-query-params [uri]
  (let [timestamp (str (time/local-date-time))
        nonce (crypt-rand/base64 32)
        params {"authid" auth-id "time" timestamp "nonce" nonce}
        query-string (str uri "?" (client/generate-query-string params))
        hashcode (create-hash query-string secret)]
    {:query-params
     {"authid" auth-id "time" timestamp "nonce" nonce "sign" hashcode}}))

(defn job-sexp [script inputs options]
  (let [script-url (str ws-url "/script/" script)]
    [:jobRequest {:xmlns "http://www.daisy.org/ns/pipeline/data"}
     [:script {:href script-url}]
     (for [[port file] inputs]
       [:input {:name (name port)}
        [:item {:value (str "file:" (url-encode file))}]])
     (for [[key value] options]
       [:option {:name (name key)} value])]))

(defn job-request [script inputs options]
  (-> (job-sexp script inputs options)
      xml/sexp-as-element
      xml/emit-str))

(defn wait [job])

(defn jobs []
  (let [url (str ws-url "/jobs")
        response (client/get url (auth-query-params url))]
    (when (client/success? response)
      (-> response :body xml/parse-str))))

(defn job [id]
  (let [url (str ws-url "/jobs/" id)
        response (client/get url (auth-query-params url))]
    (when (client/success? response)
      (-> response :body xml/parse-str))))

(defn job-create [script inputs options]
  (let [url (str ws-url "/jobs")
        request (job-request script inputs options)
        auth (auth-query-params url)
        body {:body request}
        response (client/post url (merge auth body))]
    (when (client/success? response)
      (-> response :body xml/parse-str))))

(defn job-result-1 [url]
  (let [response (client/get url (auth-query-params url))]
    (when (client/success? response)
      (-> response :body xml/parse-str))))

(defn job-result
  ([id]
     (job-result-1 (str ws-url "/jobs/" id "/result")))
  ([id type name]
     (job-result-1 (str ws-url "/jobs/" id "/result/" type "/" name)))
  ([id type name idx]
     (job-result-1 (str ws-url "/jobs/" id "/result/" type "/" name "/idx/" idx))))

(defn job-log [id]
  (let [url (str ws-url "/jobs/" id "/log")
        response (client/get url (auth-query-params url))]
    (when (client/success? response)
      (-> response :body))))

(defn job-delete [id]
  (let [url (str ws-url "/jobs/" id)
        response (client/delete url (auth-query-params url))]
    (client/success? response)))

(defn scripts []
  (let [url (str ws-url "/scripts")
        response (client/get url (auth-query-params url))]
    (when (client/success? response)
      (-> response :body xml/parse-str))))

(defn script [id]
  (let [url (str ws-url "/scripts/" id)
        response (client/get url (auth-query-params url))]
    (when (client/success? response)
      (-> response :body xml/parse-str))))

(defn alive? []
  (let [url (str ws-url "/alive")]
    (client/success? (client/get url))))

(defn get-id [job]
  (-> job xml-zip (xml1-> (attr :id))))

(defn- get-status [job]
  (-> job xml-zip (xml1-> (attr :status))))

(defn get-results [job]
  ;; unfortunately `(xml-> :results :result :result (attr :href))`
  ;; doesn't work here as `xml->` has a problem with that, see
  ;; https://dev.clojure.org/jira/browse/DZIP-6
  (-> job xml-zip (xml-> (qname "results") (qname "result") zf/children (attr :href))))

(defn get-stream [url]
  (let [response (client/get url (merge (auth-query-params url) {:as :stream}))]
    (when (client/success? response)
      (-> response :body))))

(defmacro with-job
  [[job job-create-form] & body]
  `(let [~job ~job-create-form]
     (try
       ~@body
       (finally
         (when ~job
           (job-delete (get-id ~job)))))))

(defn wait-for-result [job]
  (Thread/sleep poll-interval) ; wait a bit before polling the first time
  (let [id (get-id job)]
    (loop [result (get-job id)]
      (let [status (get-status result)]
        (if (= "RUNNING" status)
          (do
            (Thread/sleep poll-interval)
            (recur (get-job id)))
          result)))))

(defn create-job-and-wait [script inputs options]
  (let [id (get-id (job-create script inputs options))]
    (Thread/sleep poll-interval) ; wait a bit before polling the first time
    (loop [result (get-job id)]
      (let [status (get-status result)
            _ (println "Status: " status)]
        (if (= "RUNNING" status)
          (do
            (Thread/sleep poll-interval)
            (recur (get-job id)))
          result)))))
