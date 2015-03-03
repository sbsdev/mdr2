(ns mdr2.handler
  "Main entry points to the application"
  (:require [compojure.core :refer [defroutes routes GET POST]]
            [compojure.handler :as handler]
            [hiccup.middleware :refer [wrap-base-url]]
            [compojure.route :as route]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [ring.util.response :as response]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]
            [ring.middleware.stacktrace :as stacktrace]
            [mdr2.db :as db]
            [mdr2.views :as views]))

(defroutes app-routes
  "Main routes for the application"
  (GET "/" request (friend/authenticated (views/home request)))

  ;; bulk import of productions
  (GET "/production/upload" request
       (friend/authenticated (views/production-bulk-import-form request)))
  (POST "/production/upload-confirm" [file :as r]
        (friend/authenticated (views/production-bulk-import-confirm r file)))
  (POST "/production/upload" [productions]
        (friend/authenticated (views/production-bulk-import productions)))

  ;; repair productions
  (GET "/production/repair" request
       (friend/authenticated (views/production-repair-form request)))
  (POST "/production/repair-confirm" [identifier :as r]
        (friend/authenticated (views/production-repair-confirm r identifier)))
  (POST "/production/repair" [id]
        (friend/authenticated (views/production-repair id)))

  ;; individual productions
  (GET "/production/:id.xml" [id]
       (friend/authenticated (views/production-xml id)))
  (GET "/production/:id/upload" [id :as r]
       (friend/authenticated (views/file-upload-form r id)))
  (POST "/production/:id/upload" [id file :as r]
        (friend/authenticated (views/production-add-xml r id file)))
  (POST "/production/:id/state" [id state]
        (friend/authenticated (views/production-set-state id state)))
  (GET "/production/:id/split" [id :as r]
       (friend/authenticated (views/production-split-form r id)))
  (POST "/production/:id/split" [id volumes sample-rate bitrate]
        (friend/authenticated (views/production-split id volumes sample-rate bitrate)))

  ;; catalog
  (GET "/catalog" request
       (friend/authenticated (views/catalog request)))
  (POST "/catalog/:id" [id library_signature :as r]
        (friend/authenticated (views/production-catalog r id library_signature)))
  (GET "/production/:id" [id :as r]
       (friend/authenticated (views/production r id)))
  (GET "/production/:id/delete" [id]
       (friend/authorize #{:admin} (views/production-delete id)))

  ;; production monitoring
  (GET "/psm.csv" [] (views/production-monitoring))

  ;; auth
  (GET "/login" [] (views/login-form))
  (GET "/logout" req (friend/logout* (response/redirect "/")))

  ;; resources and 404
  (route/resources "/")
  (route/not-found "Not Found"))

(defroutes api-routes
  "REST API for ABACUS requests"
  (POST "/new" [f] (views/abacus-new f))
  (POST "/recorded" [f] (views/abacus-recorded f))
  (POST "/status" [f] (views/abacus-status f))
  (POST "/metadata" [f] (views/abacus-metadata f))
  (route/not-found "Not Found"))

(def site
  "Main handler for the application"
  (-> app-routes
      (friend/authenticate
       {:credential-fn (partial creds/bcrypt-credential-fn db/get-user)
        :workflows [(workflows/interactive-form)]
        :unauthorized-handler views/unauthorized})
      (wrap-defaults (assoc-in site-defaults [:static :resources] false))
      stacktrace/wrap-stacktrace
      wrap-base-url))

(def rest-api
  "REST API for ABACUS handler"
  (-> api-routes
      (wrap-defaults (assoc-in api-defaults [:params  :multipart] true))
      stacktrace/wrap-stacktrace-log))


