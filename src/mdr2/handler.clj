(ns mdr2.handler
  "Main entry points to the application"
  (:require [compojure.core :refer [defroutes routes wrap-routes GET POST]]
            [compojure.handler :as handler]
            [hiccup.middleware :refer [wrap-base-url]]
            [compojure.route :as route]
            [cemerick.friend :as friend]
            (cemerick.friend [workflows :as workflows]
                             [credentials :as creds])
            [ring.util.response :as response]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults api-defaults]]
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
  (POST "/catalog/:id" [id library_signature]
        (friend/authenticated (views/production-catalog id library_signature)))
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
  (POST "/abacus/new" [f] (views/abacus-new f))
  (POST "/abacus/recorded" [f] (views/abacus-recorded f))
  (POST "/abacus/status" [f] (views/abacus-status f))
  (POST "/abacus/metadata" [f] (views/abacus-metadata f)))

(def site
  "Main handler for the application"
  (-> app-routes
      (friend/authenticate
       {:credential-fn (partial creds/bcrypt-credential-fn db/get-user)
        :workflows [(workflows/interactive-form)]
        :unauthorized-handler views/unauthorized})
      (wrap-defaults (assoc-in site-defaults [:static :resources] false))
      wrap-base-url))

(def rest-api
  "REST API for ABACUS handler"
  (-> api-routes
      ;; FIXME: Serving app and api routes with different middleware
      ;; using Ring and Compojure is not without complications, see
      ;; http://stackoverflow.com/q/28016968. Both set of routes
      ;; should use wrap-routes. However this seems to cause problems
      ;; with friend. So we're just using wrap-routes for the
      ;; api-routes and leave the app-routes as is. This seems to work
      ;; but feels hackish. Maybe the api-routes should be split into
      ;; a separate application.
      (wrap-routes wrap-defaults (assoc-in api-defaults [:params  :multipart] true))))

(def app (routes rest-api site))

