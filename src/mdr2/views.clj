(ns mdr2.views
  (:require [clojure.string :refer [capitalize]]
            [ring.util.response :as response]
            [hiccup.form :as form]
            [hiccup.element :refer [link-to]]
            [cemerick.friend :as friend]
            [me.raynes.fs :as fs]
            [mdr2.production :as prod]
            [mdr2.vubis :as vubis]
            [mdr2.layout :as layout]
            [mdr2.dtbook :refer [dtbook]]
            [mdr2.dtbook.validation :refer [validate-metadata]]
            [mdr2.pipeline1 :as pipeline]))

(defn home [request]
  (let [identity (friend/identity request)
        user (friend/current-authentication request)]
    (layout/common user
     [:h1 "Productions"]
     (layout/button-group [{:href "/production/upload" :icon "upload"}])
     [:table.table.table-striped
      [:thead [:tr [:th "Title"] [:th "State"] [:th "Action"]]]
      [:tbody
       (for [p (prod/find-all)]
         [:tr
          [:td (link-to (str "/production/" (:id p)) (:title p))]
          [:td (:state p)]
          [:td
           (layout/button-group
            (remove nil? [{:href (str "/production/" (:id p) ".xml")
                           :icon "download"}
                          {:href (str "/production/" (:id p) "/upload")
                           :icon "upload"}
                          (when (friend/authorized? #{:admin} identity)
                            {:href (str "/production/" (:id p) "/delete")
                             :icon "trash"})]))]])]])))

(defn production [request id]
  (let [p (prod/find id)
        user (friend/current-authentication request)]
    (layout/common user
     [:h1 (str "Production: " (:title p))]
     [:p (:author p)])))

(defn production-xml [id]
  (let [production (prod/find id)]
    (-> (dtbook production)
        response/response
        (response/content-type "text/xml"))))

(defn file-upload-form [request id & [errors]]
  (let [p (prod/find id)
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

(defn production-add-xml [request id file]
  (let [{tempfile :tempfile} file
        path (.getPath tempfile)
        production (prod/find id)
        errors (concat 
                (pipeline/validate path) ; validate XML
                (validate-metadata path production))] ; validate meta data
    (if (seq errors)
      (file-upload-form request id errors)
      (do
        ;; store the xml
        (fs/move tempfile (prod/xml-path id))
        ;; and redirect to the index
        (response/redirect "/")))))

(defn production-delete [id]
  (prod/delete id)
  (response/redirect "/"))

(defn production-bulk-import-form [request & [errors]]
  (let [user (friend/current-authentication request)]
    (layout/common user
     [:h1 "Upload new productions from Vubis XML"]
     (when (seq? errors)
       [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
     (form/form-to
      {:enctype "multipart/form-data"}
      [:post (str "/production/upload-confirm")]
      (form/file-upload "file")
      (form/submit-button "Upload")))))

(defn production-bulk-import-confirm-form [request productions]
  (let [user (friend/current-authentication request)
        keys [:title :creator :source :description :libraryNumber :sourcePublisher :sourceDate]]
    (layout/common
     user
     [:h1 "Productions to import"]
     [:table.table.table-striped
      [:thead [:tr (for [k keys]
                     [:th (capitalize (name k))])]]
      [:tbody
       (for [p productions]
         [:tr (for [k keys]
                [:td (get p k)])])]]
     (form/form-to
      [:post "/production/upload"]
        (for [p productions]
          (for [k keys]
            (form/with-group "productions"
              (form/with-group (:libraryNumber p)
                (form/hidden-field k (get p k))))))
      (form/submit-button {:class "btn btn-default"} "Confirm")))))

(defn production-bulk-import-confirm
  [request file]
  (let [{tempfile :tempfile} file
        errors (vubis/validate (.getPath tempfile))]
    (if (seq errors)
      (production-bulk-import-form request errors)
      (production-bulk-import-confirm-form request (vubis/read-file tempfile)))))

(defn production-bulk-import
  [request productions]
  (let [user (friend/current-authentication request)]
    (doseq [[_ p] productions]
      (prod/update-or-create! p))
    (response/redirect "/")))

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
