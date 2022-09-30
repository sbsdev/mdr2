(ns mdr2.pipeline2.core
  "Thin layer on top of the [Pipeline2 Web Service
  API](https://daisy.github.io/pipeline/WebServiceAPI)"
  (:require [clj-http.client :as client]
            [clj-http.util :refer [url-encode]]
            [clojure.data.codec.base64 :as b64]
            [clojure.data.xml :as xml]
            [clojure.data.zip :as zf]
            [clojure.data.zip.xml :refer [attr xml-> xml1->]]
            [clojure.java.io :as io]
            [clojure.zip :refer [xml-zip]]
            [crypto.random :as crypt-rand]
            [java-time :as time]
            [mdr2.config :refer [env]]
            [pandect.algo.sha1 :as pandect]
            [clojure.tools.logging :as log])
  (:import [java.util.zip ZipEntry ZipOutputStream]))

(def ws-url "http://localhost:8181/ws")

(def ^:private timeout 1000)
(def ^:private poll-interval 3000)

(defn- create-hash [message signing-key]
  (-> (pandect/sha1-hmac-bytes message signing-key)
      b64/encode
      String.))

(defn auth-query-params [uri]
  (let [timestamp (time/format :iso-local-date-time (time/local-date-time))
        nonce (crypt-rand/base64 32)
        auth-id (env :pipeline2-auth-id)
        params {"authid" auth-id "time" timestamp "nonce" nonce}
        query-string (str uri "?" (client/generate-query-string params))
        hashcode (create-hash query-string (env :pipeline2-secret))]
    {:query-params
     {"authid" auth-id "time" timestamp "nonce" nonce "sign" hashcode}}))

(def qname (partial xml/qname "http://www.daisy.org/ns/pipeline/data"))

(defn job-sexp [script inputs options]
  (let [script-url (str ws-url "/scripts/" script)]
    [(qname "jobRequest")
     [(qname "script") {:href script-url}]
     (for [[port file] inputs]
       [(qname "input") {:name (name port)}
        [(qname "item") {:value (if-not (env :pipeline2-remote)
                         (str "file:" (url-encode file))
                         (url-encode (.getName (io/file file))))}]])
     (for [[key value] options]
       [(qname "option") {:name (name key)} value])]))

(defn job-request [script inputs options]
  (-> (job-sexp script inputs options)
      xml/sexp-as-element
      xml/emit-str))

(defn jobs []
  (let [url (str ws-url "/jobs")
        response (client/get url (auth-query-params url))]
    (when (client/success? response)
      (-> response :body xml/parse-str))))

(defn get-job [id]
  (let [url (str ws-url "/jobs/" id)
        response (client/get url (merge (auth-query-params url)
                                        #_{:socket-timeout timeout :conn-timeout timeout}))]
    (when (client/success? response)
      (-> response :body xml/parse-str))))

(defn- zip-files [files]
  (let [tmp-name (.getAbsolutePath (java.io.File/createTempFile "pipeline2-client" ".zip"))]
    (with-open [zip (ZipOutputStream. (io/output-stream tmp-name))]
      (doseq [f (map io/file files)]
        (.putNextEntry zip (ZipEntry. (.getName f)))
        (io/copy f zip)
        (.closeEntry zip)))
    tmp-name))

(defn- multipart-request [inputs body]
  {:multipart
   [{:name "job-data" :content (io/file (zip-files (vals inputs)))}
    {:name "job-request" :content body}]})

(defn- maybe-multipart [inputs body]
  (if (empty? inputs) body (multipart-request inputs body)))

(defn job-create [script inputs options]
  (let [url (str ws-url "/jobs")
        request (job-request script inputs options)
        auth (auth-query-params url)
        body {:body request}
        multipart (maybe-multipart inputs request)
        ;;response (client/post url (merge multipart auth))]
        response (client/post url (merge body auth))]
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
