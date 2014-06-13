(ns mdr2.web
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [clojure.string :as str]
            [mdr2.layout :as layout]
            [mdr2.db :as db]))

(defn home []
  (layout/common [:h1 "Productions"]
                 [:table.table.table-striped
                  [:thead [:tr [:th "Title"] [:th "State"] [:th "Action"]]]
                  [:tbody
                   (for [p (db/get-productions)]
                     [:tr 
                      [:td (:title p)]
                      [:td (:state p)]
                      [:td 
                       [:div.btn-toolbar {:role "toolbar"}
                        [:div.btn-group
                         [:button.btn.btn-default {:type "button"}
                          [:span.glyphicon.glyphicon-headphones]]
                         [:button.btn.btn-default {:type "button"}
                          [:span.glyphicon.glyphicon-forward]]
                         [:button.btn.btn-default {:type "button"}
                          [:span.glyphicon.glyphicon-wrench]]
                         [:button.btn.btn-default {:type "button"}
                          [:span.glyphicon.glyphicon-trash]]]]]])]]))

(defroutes app-routes
  (GET "/" [] (home))
  (route/resources "/")
  (route/not-found "Not Found"))

(def app
  (handler/site app-routes))
