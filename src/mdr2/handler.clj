(ns mdr2.handler
  (:require [compojure.core :refer [defroutes routes GET]]
            [compojure.handler :as handler]
            [hiccup.middleware :refer [wrap-base-url]]
            [compojure.route :as route]
            [mdr2.views :as views]))

(defroutes app-routes
  (GET "/" [] (views/home))
  (GET "/production/:id.xml" [id] (views/production-xml id))
  (GET "/production/:id" [id] (views/production id))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (-> (routes app-routes)
      (handler/site)
      (wrap-base-url)))
