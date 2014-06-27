(ns mdr2.handler
  "Main entry points to the application"
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.handler :as handler]
            [hiccup.middleware :refer [wrap-base-url]]
            [compojure.route :as route]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [ring.util.response :as response]
            [mdr2.db :as db]
            [mdr2.views :as views]))

(defroutes app-routes
  "Main routes for the application"
  (GET "/" request (views/home request))
  (GET "/production/:id.xml" [id] (friend/authenticated (views/production-xml id)))
  (GET "/production/:id/upload" [id :as r] (friend/authenticated (views/file-upload-form r id)))
  (POST "/production/:id/upload" [id file] (friend/authenticated (views/production-add-xml id file)))
  (GET "/production/:id" [id :as r] (friend/authenticated (views/production r id)))
  (GET "/production/:id/delete" [id] (friend/authorize #{:admin} (views/production-delete id)))
  (GET "/login" [] (views/login-form))
  (GET "/logout" req (friend/logout* (response/redirect "/")))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  "Main handler for the application"
  (-> app-routes
      (friend/authenticate 
       {:credential-fn (partial creds/bcrypt-credential-fn db/get-user)
        :workflows [(workflows/interactive-form)]
        :unauthorized-handler views/unauthorized})
      handler/site
      wrap-base-url))
