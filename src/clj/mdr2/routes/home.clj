(ns mdr2.routes.home
  (:require
   [mdr2.layout :as layout]
   [mdr2.db.core :as db]
   [clojure.java.io :as io]
   [mdr2.middleware :as middleware]
   [ring.util.response]
   [ring.util.http-response :as response]))

(defn home-page [request]
  (layout/render request "home.html"))

(defn home-routes []
  [""
   {:middleware [middleware/wrap-csrf
                 middleware/wrap-formats]}
   ["/" {:get home-page}]])

