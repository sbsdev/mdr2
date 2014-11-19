(ns mdr2.pipeline2
  (:require [clojure.data.xml :as xml]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :refer [xml-> xml1-> attr text]]
            [clojure.string :as s]
            [clj-time.core :as t]
            [clj-time.format :as f]
            [pandect.core :as pandect]
            [clojure.data.codec.base64 :as b64]
            [crypto.random :as crypt-rand]
            [clj-http.client :as client]
            [clj-http.util :refer [url-encode]]))

(def ws-url "http://localhost:8181/ws")

(def ^:private iso-formatter (f/formatters :date-time-no-ms))
(def ^:private auth-id "clientid")
(def ^:private secret "supersekret")
(def ^:private remote false)

(defn- create-hash [message signing-key]
  (-> (pandect/sha1-hmac-bytes message signing-key)
      b64/encode
      String.))

(defn auth-query-params [uri]
  (let [timestamp (f/unparse iso-formatter (t/now))
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

(defn post [script inputs options]
  (let [url (str ws-url "/jobs")
        request (job-request script inputs options)
        auth (auth-query-params url)
        body {:body request}
        response (client/post url (merge auth body))]
    (when (client/success? response)
      (-> response
          :body
          xml/parse-str))))

(defn wait [job])

(defn jobs []
  (let [url (str ws-url "/jobs")
        response (client/get url (auth-query-params url))]
    (when (client/success? response)
      (-> response
          :body
          xml/parse-str))))

(defn scripts []
  (let [url (str ws-url "/scripts")
        response (client/get url (auth-query-params url))]
    (when (client/success? response)
      (-> response
          :body
          xml/parse-str))))

(defn script [id]
  (let [url (str ws-url "/scripts/" id)
        response (client/get url (auth-query-params url))]
    (when (client/success? response)
      (-> response
          :body
          xml/parse-str))))

(defn alive? []
  (let [url (str ws-url "/alive")]
    (client/success? (client/get url))))
