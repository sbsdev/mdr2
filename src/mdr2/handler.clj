(ns mdr2.handler
  "Main entry points to the application"
  (:require [compojure.core :refer [defroutes routes GET POST DELETE]]
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
       (friend/authorize #{:admin :it} (views/production-bulk-import-form request)))
  (POST "/production/upload-confirm" [file :as r]
        (friend/authorize #{:admin :it} (views/production-bulk-import-confirm r file)))
  (POST "/production/upload" [productions]
        (friend/authorize #{:admin :it} (views/production-bulk-import productions)))

  ;; repair productions
  (GET "/production/repair" request
       (friend/authorize #{:admin :studio :it} (views/production-repair-form request)))
  (POST "/production/repair-confirm" [identifier :as r]
        (friend/authorize #{:admin :studio :it} (views/production-repair-confirm r identifier)))
  (POST "/production/repair" [id]
        (friend/authorize #{:admin :studio :it} (views/production-repair id)))

  ;; individual productions read-only
  (GET "/production/:id.xml" [id]
       (friend/authenticated (views/production-xml id)))
  (GET "/production/:id" [id :as r]
       (friend/authenticated (views/production r id)))

  ;; delete individual production
  (DELETE "/production/:id" [id]
       (friend/authorize #{:it} (views/production-delete id)))

  ;; upload structure for individual production
  (GET "/production/:id/upload" [id :as r]
       (friend/authorize #{:admin :etext :it} (views/file-upload-form r id)))
  (POST "/production/:id/upload" [id file :as r]
        (friend/authorize #{:admin :etext :it} (views/production-add-xml r id file)))

  ;; modify state of an individual production
  (POST "/production/:id/state" [id state]
        (friend/authorize #{:admin :it} (views/production-set-state id state)))

  ;; split an individual production
  (GET "/production/:id/split" [id :as r]
       (friend/authorize #{:admin :it} (views/production-split-form r id)))
  (POST "/production/:id/split" [id volumes sample-rate bitrate :as r]
        (friend/authorize #{:admin :it} (views/production-split r id volumes sample-rate bitrate)))

  ;; archived productions
  (GET "/production/archived/:year{\\d{4}}/:month{\\d{1,2}}" [year month :as r]
       (friend/authenticated (views/production-archived r year month)))
  (GET "/production/archived/:year{\\d{4}}" [year :as r]
       (friend/authenticated (views/production-archived r year)))
  (GET "/production/archived" r (friend/authenticated (views/production-archived r)))

  ;; catalog
  (GET "/catalog" request
       (friend/authorize #{:catalog :it} (views/catalog request)))
  (POST "/catalog/:id" [id library_signature :as r]
        (friend/authorize #{:catalog :it} (views/production-catalog r id library_signature)))

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
      (wrap-defaults (assoc-in api-defaults [:params :multipart] true))
      stacktrace/wrap-stacktrace-log))
