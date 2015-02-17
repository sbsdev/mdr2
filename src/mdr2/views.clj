(ns mdr2.views
  (:require [clojure.string :as string]
            [ring.util.response :as response]
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
            [mdr2.repair :as repair]
            [mdr2.pipeline1 :as pipeline]))

(defn home [request]
  (let [identity (friend/identity request)
        user (friend/current-authentication request)]
    (layout/common user
     [:h1 "Productions"]
     [:table.table.table-striped
      [:thead [:tr [:th "Title"] [:th "Type"] [:th "State"] [:th "Action"]]]
      [:tbody
       (let [cached-state (memoize db/find-state)
             cached-production-type (memoize db/find-production-type)]
         (for [{:keys [id title production_type state] :as production} (prod/find-all)]
           (let [realized-state (first (cached-state {:id state}))
                 realized-production-type (first (cached-production-type {:id production_type}))]
             [:tr
              [:td (link-to (str "/production/" id) title)]
              [:td (:name realized-production-type)]
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
                    (cond
                      ;; enable the "Recorded" button if the next state is "recorded",
                      ;; there is an DAISY export and the production has been imported from
                      ;; the libary, i.e. is not handled via ABACUS or
                      ;; the production has already a library signatur
                      ;; as is the case with productions that are
                      ;; repaired
                      (and (= next-state "recorded")
                           (or (:library_number production) (:library_signature production))
                           (prod/manifest? production))
                      (form/form-to
                       {:class "btn-group"} [:post (str "/production/" id "/state")]
                       (form/hidden-field :state next-state)
                       (anti-forgery-field)
                       [:button.btn.btn-default (layout/glyphicon "transfer") " "
                        (:name (first (cached-state {:id next-state})))])
                      ;; Enable the "Split" button if the next state is "split" and there
                      ;; is a split production
                      (and (= next-state "split") (prod/split? production))
                      [:a.btn.btn-default
                       {:href (str "/production/" id "/split")}
                       (layout/glyphicon "transfer") " "
                       (:name (first (cached-state {:id next-state})))]
                      ;; in all other cases disable the button
                      :else [:button.btn.btn-default
                             {:disabled "disabled"}
                             (layout/glyphicon "transfer") " "
                             (:name (first (cached-state {:id next-state})))]))
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
        (response/content-type "text/xml")
        (response/charset "UTF-8"))))

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
  (let [p (assoc (prod/find id) :library_signature library_signature)]
    ;; the state is implicitly set to :cataloged if the
    ;; library_signature is set
    (prod/set-state! p "cataloged")
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

(defn production-repair-form
  [request & [errors]]
  (let [user (friend/current-authentication request)]
    (layout/common user
     [:h1 "Production to repair"]
     (when (seq errors)
       [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
     (form/form-to
      [:post "/production/repair-confirm"]
      (anti-forgery-field)
      [:div.form-group
       (form/label "identifier" "Search term")
       (form/text-field {:class "form-control"
                         :placeholder "DAM number, DS number, DY number, Title or Author"}
                        "identifier")]
      (form/submit-button {:class "btn btn-default"} "Repair")))))

(defn production-repair-confirm
  [request identifier]
  (if (string/blank? identifier)
    (production-repair-form request ["Search term should not be empty"])
    (let [productions
          (cond
            (repair/production-id? identifier)
            (when-let [production (prod/find (.substring identifier 3))] [production])
            (repair/library-signature? identifier)
            (prod/find-by-library-signature identifier)
            (repair/product-number? identifier)
            (prod/find-by-productnumber identifier)
            :else (let [search-term (str "%" identifier "%")]
                    (prod/find-by-title-or-creator search-term)))
          user (friend/current-authentication request)]
      (layout/common
       user
       [:h1 "Productions to repair"]
       [:table.table.table-striped
        [:thead [:tr [:th "Title"] [:th "Creator"] [:th "Action"]]]
        [:tbody
         (for [p productions]
           [:tr
            [:td (:title p)]
            [:td (:creator p)]
            [:td
             (form/form-to
              {:class "btn-group"}
              [:post "/production/repair"]
              (form/hidden-field :id (:id p))
              (anti-forgery-field)
              [:button.btn.btn-default (layout/glyphicon "wrench") " Repair"])]])]]
       (when (empty? productions)
         [:div.alert.alert-warning {:role "alert"} "No Data to display"])))))

(defn production-repair
  [id]
  (let [production (prod/find id)]
    (repair/repair production)
    ;; FIXME: might be worth it to set a flash message saying that the
    ;; repair has been initiated
    (response/redirect "/")))


(defn production-set-state [id state]
  (let [production (prod/find id)]
    (case state
      "recorded" (prod/set-state-recorded! production)
      (prod/set-state! production state))
    (response/redirect "/")))

(defn production-split-form [request id]
  (let [user (friend/current-authentication request)
        production (prod/find id)] ; FIXME: the find could return nil
    (layout/common
     user
     [:h1 "Split Production"]
     (form/form-to
      {:class "form-horizontal"}
      [:post (str "/production/" id "/split")]
      (anti-forgery-field)
      [:div.form-group
       (form/label {:class "col-sm-2 control-label"} :volumes "Volumes:")
       [:div.col-sm-2
        (form/drop-down {:class "form-control"} :volumes [1 2 3 4 5 6 7 8] 2)]]
      [:div.form-group
       (form/label {:class "col-sm-2 control-label"} :sample-rate "Sample Rate:")
       [:div.col-sm-2
        (form/drop-down {:class "form-control"} :sample-rate [11025 22050 44100 48000] 22050)]]
      ;; [:div.form-group
      ;;  (form/label {:class "col-sm-2 control-label"} :sample-rate "Sample Rate:")
      ;;  [:label.radio-inline
      ;;   (form/radio-button :sample-rate false 11025) 11025]
      ;;  [:label.radio-inline
      ;;   (form/radio-button :sample-rate true 22050) 22050]
      ;;  [:label.radio-inline
      ;;   (form/radio-button :sample-rate false 44100) 44100]
      ;;  [:label.radio-inline
      ;;   (form/radio-button :sample-rate false 48000) 48000]]
      [:div.form-group
       (form/label {:class "col-sm-2 control-label"} :bitrate "Bitrate:")
       [:div.col-sm-2
        (form/drop-down {:class "form-control"} :bitrate [32 48 56 64 128] 56)]]
      [:div.form-group
       [:div {:class "col-sm-offset-2 col-sm-2"}
       (form/submit-button {:class "btn btn-default"} "Encode")]]))))

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
