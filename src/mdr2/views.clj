(ns mdr2.views
  (:require [ring.util.response :as response]
            [hiccup.form :as form]
            [hiccup.element :refer [link-to]]
            [cemerick.friend :as friend]
            [me.raynes.fs :as fs]
            [immutant.messaging :as msg]
            [mdr2.queues :as queues]
            [mdr2.production :as prod]
            [mdr2.state :as state]
            [mdr2.vubis :as vubis]
            [mdr2.layout :as layout]
            [mdr2.dtbook :refer [dtbook]]
            [mdr2.dtbook.validation :refer [validate-metadata]]
            [mdr2.production-monitoring :as psm]
            [mdr2.pipeline1 :as pipeline]))

(defn home [request]
  (let [identity (friend/identity request)
        user (friend/current-authentication request)]
    (layout/common user
     [:h1 "Productions"]
     (layout/button-group
      [(layout/button "/production/upload" (layout/glyphicon "upload"))])
     [:table.table.table-striped
      [:thead [:tr [:th "Title"] [:th "State"] [:th "Action"]]]
      [:tbody
       (for [{:keys [id title state] :as production} (prod/find-all)]
         [:tr
          [:td (link-to (str "/production/" id) title)]
          [:td (state/to-str state)]
          [:td
           (layout/button-group
            (remove
             nil?
             [(layout/button (str "/production/" id ".xml")
                             (layout/glyphicon "download"))
              (layout/button (str "/production/" id "/upload")
                             (layout/glyphicon "upload"))
              (when-let [next-state (first (state/next-states state))]
                (form/form-to {:class "btn-group"}
                              [:post (str "/production/" id "/state")]
                              (form/hidden-field :state next-state)
                              [:button.btn.btn-default
                               ;; only allow setting the state to
                               ;; recorded if there is a DAISY export
                               (when-not (and (= next-state :recorded)
                                              (not (prod/manifest? production)))
                                 {:disabled "disabled"})
                               (layout/glyphicon "transfer") " " (state/to-str next-state)]))
              ;; (layout/dropdown (for [next (state/next-states state)]
              ;;                    (layout/menu-item "#" (state/to-str next)))
              ;;                  (layout/glyphicon "transfer"))
              (when (friend/authorized? #{:admin} identity)
                (layout/button (str "/production/" id "/delete")
                               (layout/glyphicon "trash")))]))]])]])))
(defn production [request id]
  (let [p (prod/find id)
        user (friend/current-authentication request)]
    (layout/common user
     [:h1 (str "Production: " (:title p))]
     (for [[k v] (sort-by first (seq p))]
       [:p [:b (layout/key-to-label k) ":"] " " v]))))

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
     (when (seq errors)
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
        ;; add the file
        (prod/add-structure production tempfile)
        ;; and redirect to the index
        (response/redirect "/")))))

(defn catalog [request]
  (let [identity (friend/identity request)
        user (friend/current-authentication request)]
    (layout/common user
                   [:h1 "Productions"]
     [:table.table.table-striped
      [:thead [:tr [:th "Title"] [:th "Product Number"] [:th "DAM Number"] [:th "Duration"] [:th "Number of CDs"] [:th "Depth"] [:th "Narrator"] [:th "Date of Production"] [:th "Libary signature"]]]
      [:tbody
       (for [{:keys [id title product_number total_time volumes depth narrator produced_date] 
              :as production} (prod/find-by-state :encoded)]
         [:tr
          [:td (link-to (str "/production/" id) title)]
          [:td product_number]
          [:td (prod/dam-number production)]
          [:td total_time]
          [:td volumes]
          [:td depth]
          [:td narrator]
          [:td produced_date]
          [:td
           (form/form-to {:class "form-inline" :role "form"}
                         [:post (str "/catalog/" id)]
                         [:div.form-group
                          (form/label {:class "sr-only"} :library_signature "Signature")
                          (form/text-field
                           {:class "form-control" :placeholder "Enter Signature"}
                           :library_signature)
                          [:button.btn.btn-default
                           (layout/glyphicon "transfer")]])]])]])))

(defn production-catalog [request id library_signature]
  (let [user (friend/current-authentication request)
        p (assoc (prod/find id) 
            :library_signature library_signature
            ;; the state is implicitly set to :cataloged if the
            ;; library_signature is set
            :state :cataloged)]
    (prod/update! p)
    ;; put the production on the archive queue
    (msg/publish (queues/archive) p)
    (response/redirect "/")))

(defn production-delete [id]
  (prod/delete id)
  (response/redirect "/"))

(defn production-bulk-import-form [request & [errors]]
  (let [user (friend/current-authentication request)]
    (layout/common user
     [:h1 "Upload new productions from Vubis XML"]
     (when (seq errors)
       [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
     (form/form-to
      {:enctype "multipart/form-data"}
      [:post (str "/production/upload-confirm")]
      (form/file-upload "file")
      (form/submit-button "Upload")))))

(defn production-bulk-import-confirm-form [request productions]
  (let [user (friend/current-authentication request)
        keys [:title :creator :source :description :library_number :source_publisher :source_date]]
    (layout/common
     user
     [:h1 "Productions to import"]
     [:table.table.table-striped
      [:thead [:tr (for [k keys]
                     [:th (layout/key-to-label k)])]]
      [:tbody
       (for [p productions]
         [:tr (for [k keys]
                [:td (get p k)])])]]
     (form/form-to
      [:post "/production/upload"]
        (for [p productions]
          (for [k keys]
            (form/with-group "productions"
              (form/with-group (:library_number p)
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

(defn production-set-state [request id state-name]
  (let [user (friend/current-authentication request)
        state (state/from-str state-name)
        p (assoc (prod/find id) :state state)]
    (prod/update! p)
    (when (= state :recorded)
      (msg/publish (queues/encode) p))
    (response/redirect "/")))

(defn production-monitoring []
  "Return a csv containing the total audio length of all productions"
  (response/file-response (psm/csv (prod/find-by-state :structured))))

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
