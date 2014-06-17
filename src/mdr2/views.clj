(ns mdr2.views
  (:require [clojure.string :as str]
            [mdr2.db :as db]
            [mdr2.layout :as layout]))

(defn home []
  (layout/common 
   [:h1 "Productions"]
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

(defn production [id]
  (let [p (db/get-production id)]
    (layout/common 
     [:h1 (str "Production: " (:title p))]
     [:p (:author p)])))

