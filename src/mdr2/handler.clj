(ns mdr2.handler
  (:use compojure.core)
  (:require [compojure.handler :as handler]
            [compojure.route :as route]
            [mdr2.layout :as layout]
            [clojure.string :as str]))

(def productions [{:id 1 :title "foo" :state "New"}
                  {:id 2 :title "bar" :state "Recording"}
                  {:id 3 :title "baz" :state "Finished"}])

(defn home []
  (layout/common [:h1 "Productions"]
                 [:table.table.table-striped
                  [:thead [:tr [:th "Title"] [:th "State"] [:th "Action"]]]
                  [:tbody
                   (for [p productions]
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
