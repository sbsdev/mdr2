(ns mdr2.views
  (:require [ring.util.response :as response]
            [hiccup.form :as form]
            [hiccup.element :refer [link-to]]
            [cemerick.friend :as friend]
            [mdr2.db :as db]
            [mdr2.layout :as layout]
            [mdr2.dtbook :refer [dtbook]]
            [mdr2.pipeline1 :as pipeline]))

(defn home [request]
  (let [identity (friend/identity request)
        user (friend/current-authentication request)]
    (layout/common user
     [:h1 "Productions"]
     [:table.table.table-striped
      [:thead [:tr [:th "Title"] [:th "State"] [:th "Action"]]]
      [:tbody
       (for [p (db/get-productions)]
         [:tr
          [:td (link-to (str "/production/" (:id p)) (:title p))]
          [:td (:state p)]
          [:td
           [:div.btn-toolbar {:role "toolbar"}
            [:div.btn-group
             [:a.btn.btn-default {:href (str "/production/" (:id p) ".xml")}
              [:span.glyphicon.glyphicon-download]]
             [:a.btn.btn-default {:href (str "/production/" (:id p) "/upload")}
              [:span.glyphicon.glyphicon-upload]]
             (when (friend/authorized? #{:admin} identity)
               [:a.btn.btn-default {:href (str "/production/" (:id p) "/delete")}
                [:span.glyphicon.glyphicon-trash]])]]]])]])))

(defn production [request id]
  (let [p (db/get-production id)
        user (friend/current-authentication request)]
    (layout/common user
     [:h1 (str "Production: " (:title p))]
     [:p (:author p)])))

(defn production-xml [id]
  (let [production (db/get-production id)]
    (-> (dtbook production)
        response/response
        (response/content-type "text/xml"))))

(defn file-upload-form [request id & [errors]]
  (let [p (db/get-production id)
        user (friend/current-authentication request)]
    (layout/common user
     [:h1 "Upload"]
     [:p (str "Upload structure for " (:title p))]
     (when (seq? errors)
       [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
     (form/form-to
      {:enctype "multipart/form-data"}
      [:post (str "/production/" id "/upload")]
      (form/file-upload "file")
      (form/submit-button "Upload")))))

(defn production-add-xml [id file]
  (let [{tempfile :tempfile} file
        errors (pipeline/validate (.getPath tempfile))]
    (if (seq? errors)
      (file-upload-form id errors)
      (do
        ;; store the xml
        ;; and add a reference to the path in the db
        ;; finally redirect to the index
        (response/redirect "/")))))

(defn production-delete [id]
  (db/delete-production id)
  (response/redirect "/"))

(defn login-form []
  (layout/common nil
   [:h3 "Login"]
   (form/form-to
    [:post "/login"]
    [:div.form-group
     (form/label "username" "Username:")
     (form/text-field {:class "form-control"} "username")]
    [:div.form-group
     (form/label "password" "Password:")
     (form/password-field {:class "form-control"} "password")]
    (form/submit-button {:class "btn btn-default"} "Login"))))

(defn unauthorized [request]
  (let [user (friend/current-authentication request)]
    (->
     (layout/common user
      [:h2
       [:div.alert.alert-danger
        "Sorry, you do not have sufficient privileges to access "
        (:uri request)]]
      [:p "Please ask an administrator for help"])
     response/response
     (response/status 401))))
