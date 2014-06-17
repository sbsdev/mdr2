(ns mdr2.views
  (:require [clojure.data.xml :as xml]
            [ring.util.response :as ring]
            [hiccup.form :as form]
            [mdr2.db :as db]
            [mdr2.layout :as layout]
            [mdr2.dtbook :refer [dtbook]]))

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
           [:a.btn.btn-default {:href (str "/production/" (:id p) ".xml")}
            [:span.glyphicon.glyphicon-download]]
           [:a.btn.btn-default {:href (str "/production/" (:id p) "/upload")}
            [:span.glyphicon.glyphicon-upload]]]]]])]]))

(defn production [id]
  (let [p (db/get-production id)]
    (layout/common 
     [:h1 (str "Production: " (:title p))]
     [:p (:author p)])))

(defn production-xml [id]
  (let [production (db/get-production id)]
    (-> (xml/emit-str (xml/sexp-as-element (dtbook production)))
        (ring/response)
        (ring/content-type "text/xml"))))

(defn file-upload-form [id]
  (let [p (db/get-production id)]
    (layout/common
     [:h1 "Upload"]
     [:p (str "Upload structure for " (:title p))]
     (form/form-to
      {:enctype "multipart/form-data"}
      [:post (str "/production/" id "/upload")]
      (form/file-upload "file")
      (form/submit-button "Upload")))))

(defn production-add-xml [id file]
  (let [{tempfile :tempfile} file]
    (println id)
    (println tempfile)
    (ring/redirect "/")))

