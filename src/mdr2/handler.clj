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
            [ring.middleware.nested-params :as nested-params]
            [mdr2.db :as db]
            [mdr2.views :as views]))

(defroutes app-routes
  "Main routes for the application"
  (GET "/" request (views/home request))
  ;; bulk import of productions
  (GET "/production/upload" request
       (friend/authenticated (views/production-bulk-import-form request)))
  (POST "/production/upload-confirm" [file :as r]
        (friend/authenticated (views/production-bulk-import-confirm r file)))
  (POST "/production/upload" [productions :as r]
        (friend/authenticated (views/production-bulk-import r productions)))
  ;; individual productions
  (GET "/production/:id.xml" [id]
       (friend/authenticated (views/production-xml id)))
  (GET "/production/:id/upload" [id :as r]
       (friend/authenticated (views/file-upload-form r id)))
  (POST "/production/:id/upload" [id file :as r]
        (friend/authenticated (views/production-add-xml r id file)))
  (POST "/production/:id/state" [id state :as r]
        (friend/authenticated (views/production-set-state r id state)))
  (GET "/catalog" request
       (friend/authenticated (views/catalog request)))
  (POST "/catalog/:id" [id library_signature :as r]
        (friend/authenticated (views/production-catalog r id library_signature)))
  (GET "/production/:id" [id :as r]
       (friend/authenticated (views/production r id)))
  (GET "/production/:id/delete" [id]
       (friend/authorize #{:admin} (views/production-delete id)))
  ;; auth
  (GET "/login" [] (views/login-form))
  (GET "/logout" req (friend/logout* (response/redirect "/")))
  ;; resources and 404
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  "Main handler for the application"
  (-> app-routes
      (friend/authenticate
       {:credential-fn (partial creds/bcrypt-credential-fn db/get-user)
        :workflows [(workflows/interactive-form)]
        :unauthorized-handler views/unauthorized})
      handler/site ; FIXME: migrate to ring-defaults
      nested-params/wrap-nested-params
      wrap-base-url))
