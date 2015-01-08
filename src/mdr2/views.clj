(ns mdr2.views
  (:require [ring.util.response :as response]
            [hiccup.form :as form]
            [hiccup.element :refer [link-to]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [cemerick.friend :as friend]
            [me.raynes.fs :as fs]
            [immutant.messaging :as msg]
            [mdr2.queues :as queues]
            [mdr2.production :as prod]
            [mdr2.db :as db]
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
       (let [cached-state (memoize db/find-state)]
         (for [{:keys [id title state] :as production} (prod/find-all)]
           (let [realized-state (first (cached-state {:id state}))]
             [:tr
              [:td (link-to (str "/production/" id) title)]
              [:td (:name realized-state)]
              [:td
               (layout/button-group
                (remove
                 nil?
                 [(layout/button (str "/production/" id ".xml")
                                 (layout/glyphicon "download"))
                  (layout/button (str "/production/" id "/upload")
                                 (layout/glyphicon "upload"))
                  (when-let [next-state (:next_state realized-state)]
                    (form/form-to
                     {:class "btn-group"}
                     [:post (str "/production/" id "/state")]
                     (form/hidden-field :state next-state)
                     (anti-forgery-field)
                     [:button.btn.btn-default
                      ;; only allow setting the state to recorded if there is a DAISY export
                      ;; and the production has been imported from the library, i.e. is not
                      ;; handled via ABACUS
                      (when-not
                          (and (= next-state "recorded")
                               (:library_number production) ; is the product not managed by
                                        ; ABACUS
                               (prod/manifest? production)) ; is there a DAISY export?
                        {:disabled "disabled"})
                      (layout/glyphicon "transfer") " "
                      (:name (first (cached-state {:id next-state})))]))
                  ;; (layout/dropdown (for [next (state/next-states state)]
                  ;;                    (layout/menu-item "#" (state/to-str next)))
                  ;;                  (layout/glyphicon "transfer"))
                  (when (friend/authorized? #{:admin} identity)
                    (layout/button (str "/production/" id "/delete")
                                   (layout/glyphicon "trash")))]))]])))]])))

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
      (anti-forgery-field)
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
              :as production} (prod/find-by-state "encoded")]
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
                         (anti-forgery-field)
                         [:div.form-group
                          (form/label {:class "sr-only"} :library_signature "Signature")
                          (form/text-field
                           {:class "form-control" :placeholder "Enter Signature"}
                           :library_signature)
                          [:button.btn.btn-default
                           (layout/glyphicon "transfer")]])]])]])))

(defn production-catalog [id library_signature]
  (let [p (assoc
           (prod/find id)
           :library_signature library_signature
           ;; the state is implicitly set to :cataloged if the
           ;; library_signature is set
           :state "cataloged")]
    (prod/update! p)
    ;; put the production on the archive queue
    (msg/publish (queues/archive) p)
    (response/redirect "/")))

(defn production-delete [id]
  (prod/delete! id)
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
      (anti-forgery-field)
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
      (anti-forgery-field)
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
  [productions]
  (doseq [[_ p] productions]
    (prod/update-or-create! (prod/parse p)))
  (response/redirect "/"))

(defn production-set-state [id state]
  (let [production (prod/find id)]
    (case state
      "recorded" (prod/set-state-recorded! production)
      (prod/set-state! production state))
    (response/redirect "/")))

(defn production-split-form [request id]
  (let [user (friend/current-authentication request)
        production (prod/find id)]
    (layout/common
     user
     [:h1 "Split Production"]
     (form/form-to
      [:post (str "/production/" id "/split")]
      (anti-forgery-field)
      [:div.form-group
       (form/label :volumes "Volumes:")
       (form/drop-down {:class "form-control"} :volumes [1 2 3 4 5 6 7 8])]
      [:div.form-group
       (form/label :sample-rate "Sample Rate:")
       (form/drop-down {:class "form-control"} :sample-rate [11025 22050 44100 48000])]
      [:div.form-group
       (form/label :bitrate "Bitrate:")
       (form/drop-down {:class "form-control"} :bitrate [32 48 56 64 128])]
      (form/submit-button {:class "btn btn-default"} "Encode")))))

(defn production-split [id volumes sample-rate bitrate]
  (let [production (prod/find id)]
    (prod/set-state-split! production (Integer/parseInt volumes)
                           (Integer/parseInt sample-rate) (Integer/parseInt bitrate)))
  (response/redirect "/"))

(defn production-monitoring []
  "Return a csv containing the total audio length of all productions"
  (response/file-response (psm/csv (prod/find-by-state :structured))))

(defn login-form []
  (layout/common nil
   [:h3 "Login"]
   (form/form-to
    [:post "/login"]
    (anti-forgery-field)
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
