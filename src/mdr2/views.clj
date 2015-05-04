(ns mdr2.views
  "Views for web application"
  (:require [clojure.string :as string]
            [ring.util.response :as response]
            [clj-time.core :as t]
            [hiccup.form :as form]
            [hiccup.element :refer [link-to]]
            [ring.util.anti-forgery :refer [anti-forgery-field]]
            [cemerick.friend :as friend]
            [me.raynes.fs :as fs]
            [immutant.messaging :as msg]
            [mdr2.queues :as queues]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.db :as db]
            [mdr2.vubis :as vubis]
            [mdr2.abacus :as abacus]
            [mdr2.layout :as layout]
            [mdr2.dtbook :as dtbook]
            [mdr2.dtbook.validation :refer [validate-metadata]]
            [mdr2.production-monitoring :as psm]
            [mdr2.repair :as repair]
            [mdr2.pipeline1 :as pipeline])
  (:import java.sql.BatchUpdateException))

(defn home [request]
  (let [identity (friend/identity request)]
    (layout/common identity
     [:h1 "Productions"]
     (when-let [errors (:errors (:flash request))]
       [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
     (when-let [warnings (:warnings (:flash request))]
       [:p [:ul.alert.alert-warning (for [w warnings] [:li w])]])
     (when-let [message (:message (:flash request))]
       [:p.alert.alert-success message])
     [:table#productions.table.table-striped
      [:thead [:tr [:th "DAM"] [:th "Title"] [:th "Type"] [:th "State"]
               (when (friend/authorized? #{:admin :etext :it} identity) [:th.orderable-false "Action"])]]
      [:tbody
       (let [cached-state (memoize db/find-state)
             cached-production-type (memoize db/find-production-type)]
         (for [{:keys [id title production_type state] :as production} (prod/find-all-in-production)]
           (let [realized-state (first (cached-state {:id state}))
                 realized-production-type (first (cached-production-type {:id production_type}))
                 next-state (:next_state realized-state)]
             [:tr
              [:td id]
              [:td (link-to (str "/production/" id) title)]
              [:td (:name realized-production-type)]
              [:td (:name realized-state)]
              (when (friend/authorized? #{:admin :etext :it} identity)
                [:td
                 (layout/button-group
                  (remove
                   nil?
                   [;; show the download button while the production hasn't been recorded
                    (when (#{"structured" "recorded"} next-state)
                      (layout/button (str "/production/" id ".xml") (layout/glyphicon "download")))
                    ;; show the upload button while the production hasn't been recorded and
                    ;; the user is authorized
                    (when (#{"structured" "recorded"} next-state)
                      (layout/button (str "/production/" id "/upload") (layout/glyphicon "upload")))
                    (cond
                      ;; show the "Recorded" button if the next state is "recorded", the
                      ;; user is authorized, there is an DAISY export and the production has
                      ;; been imported from the libary, i.e. is not handled via ABACUS or the
                      ;; production has a revision greater than zero as is the case with
                      ;; productions that are repaired
                      (and (= next-state "recorded")
                           (or (:library_number production)
                               (> (:revision production) 0))
                           (prod/manifest? production)
                           (friend/authorized? #{:admin :it} identity))
                      (form/form-to
                       {:class "btn-group"} [:post (str "/production/" id "/state")]
                       (form/hidden-field :state next-state)
                       (anti-forgery-field)
                       [:button.btn.btn-default (layout/glyphicon "transfer") " "
                        (:name (first (cached-state {:id next-state})))])
                      ;; show the "Split" button if the next state is "split", the user is
                      ;; authorized and there is a split production
                      (and (= next-state "split")
                           (prod/split? production)
                           (friend/authorized? #{:admin :it} identity))
                      (layout/button (str "/production/" id "/split")
                                     (layout/glyphicon "transfer") " "
                                     (:name (first (cached-state {:id next-state})))))
                    ;; show the delete button if the user is authorized
                    (when (friend/authorized? #{:it} identity)
                      (form/form-to
                       {:class "btn-group"} [:post (str "/production/" id)]
                       (form/hidden-field :_method "DELETE") ; fake a delete request
                       (anti-forgery-field)
                       [:button.btn.btn-default (layout/glyphicon "trash")]))]))])])))]])))

(defn production [request id]
  (let [p (prod/find id)
        identity (friend/identity request)]
    (layout/common identity
     [:h1 (str "Production: " (:title p))]
     (for [[k v] (sort-by first (seq p))]
       [:p [:b (layout/key-to-label k) ":"] " " v]))))

(defn production-xml [id]
  (let [production (prod/find id)]
    (-> (dtbook/dtbook production)
        response/response
        (response/content-type "text/xml")
        (response/charset "UTF-8"))))

(defn file-upload-form [request id & [errors]]
  (let [p (prod/find id)
        identity (friend/identity request)]
    (layout/common identity
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
                (pipeline/validate path :dtbook) ; validate XML
                (validate-metadata path production) ; validate meta data
                ;; make sure production is in the state that allows upload
                (when (not (#{"new" "structured"} (:state production)))
                  ["Production not in state \"new\" or \"structured\""]))]
    (if (seq errors)
      (file-upload-form request id errors)
      (do
        ;; add the file
        (prod/add-structure production tempfile)
        ;; and redirect to the index
        (response/redirect-after-post "/")))))

;; catalog
(defn catalog [request & error]
  (let [identity (friend/identity request)]
    (layout/common identity
     [:h1 "Productions"]
     (when error
       [:p [:ul.alert.alert-danger [:li error]]])
     [:table.table.table-striped
      [:thead [:tr [:th "Title"] [:th "Author"] [:th "Product Number"] [:th "Library Number"] [:th "Total time"] [:th "Number of CDs"] [:th "Depth"] [:th "Narrator"] [:th "Date of Production"] [:th "Libary signature"]]]
      [:tbody
       (for [{:keys [id title creator product_number library_number
                     total_time volumes depth narrator produced_date]}
             (prod/find-by-state "encoded")]
         [:tr
          [:td (link-to (str "/production/" id) title)]
          [:td creator]
          [:td product_number]
          [:td library_number]
          [:td (quot total_time (* 1000 60))] ; in minutes
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
                           {:class "form-control" :placeholder "dsxxxxx"}
                           :library_signature)
                          [:button.btn.btn-default
                           (layout/glyphicon "transfer")]])]])]])))

(defn production-catalog [request id library_signature]
  (if (prod/library-signature? library_signature)
    (try
      (let [p (assoc (prod/find id) :library_signature library_signature)]
        (prod/set-state-cataloged! p)
        (response/redirect-after-post "/catalog"))
      (catch BatchUpdateException e
        (catalog request (str "Library signature already in use: "
                              library_signature))))
    (catalog request "Library signature not valid")))

;; archived productions
(defn production-archived-view [request productions date-str]
  (let [identity (friend/identity request)]
    (layout/common
     identity
     [:h1 (format "Archived Productions %s" date-str)]
     [:table#productions.table.table-striped
      [:thead [:tr [:th "DAM"] [:th "Title"] [:th "Author"] [:th "Product Number"] [:th "Library Number"] [:th "Total time"] [:th "Number of CDs"] [:th "Depth"] [:th "Narrator"] [:th "Date of Production"] [:th "Libary signature"]]]
      [:tbody
       (for [{:keys [id title creator product_number
                     library_number library_signature
                     total_time volumes depth narrator produced_date]}
             productions]
         [:tr
          [:td id]
          [:td (link-to (str "/production/" id) title)]
          [:td creator]
          [:td product_number]
          [:td library_number]
          [:td (if total_time
                 (quot total_time (* 1000 60)) "")] ; in minutes
          [:td volumes]
          [:td depth]
          [:td narrator]
          [:td produced_date]
          [:td library_signature]])]])))

(defn production-archived
  ([request]
   (let [date (t/today)]
     (response/redirect (format "/production/archived/%s/%s" (t/year date) (t/month date)))))
  ([request year]
   (production-archived-view
    request (prod/find-archived-by-date year) (format "%s" year)))
  ([request year month]
   (production-archived-view
    request (prod/find-archived-by-date year month) (format "%s-%s" year month))))

(defn production-delete [id]
  (prod/delete! (prod/find id))
  (-> (response/redirect-after-post "/")
      (assoc :flash {:message "Production has been deleted"})))

;; bulk import from vubis
(defn production-bulk-import-form [request & [errors]]
  (let [identity (friend/identity request)]
    (layout/common identity
     [:h1 "Upload new productions from Vubis XML"]
     (when errors
       [:p [:ul.alert.alert-danger
            (for [{:keys [error line column]} errors]
              [:li (format "%s Line: %d Column: %d" error line column)])]])
     (form/form-to
      {:enctype "multipart/form-data"}
      [:post (str "/production/upload-confirm")]
      (anti-forgery-field)
      (form/file-upload "file")
      (form/submit-button "Upload")))))

(defn production-bulk-import-confirm-form [request productions]
  (let [identity (friend/identity request)
        keys [:title :creator :source :description :library_number :source_publisher :source_date]]
    (layout/common identity
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
    (if (empty? errors)
      (production-bulk-import-confirm-form request (vubis/read-file tempfile))
      (production-bulk-import-form request errors))))

(defn production-bulk-import
  [productions]
  ;; FIXME: maybe the whole create and set structured should be in a
  ;; transaction
  (let [created (doall (map (fn [[_ p]]
                              (-> p
                                  prod/parse
                                  prod/add-default-meta-data
                                  prod/create!))
                            productions))
        errors (remove map? created)
        new (filter map? created)]
    (doseq [p new]
      (-> p
       ;; productions from vubis do not need any manual structuring.
       ;; They get their standard structure from a default template
       prod/set-state-structured!
       ;; write the dtbook template file
       dtbook/dtbook-file))
    (-> (response/redirect-after-post "/")
        (cond-> (seq errors)
          (assoc :flash {:warnings (for [e errors]
                                     (str (re-find #"PNX \d+" (first e))
                                          " has already been imported"))})))))

(defn production-repair-form
  [request & [errors]]
  (let [identity (friend/identity request)]
    (layout/common identity
     [:h1 "Production to repair"]
     (when (seq errors)
       [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
     (form/form-to
      [:post "/production/repair-confirm"]
      (anti-forgery-field)
      [:div.form-group
       (form/label "identifier" "Search term")
       (form/text-field {:class "form-control"
                         :placeholder "damxxxxx, dsxxxxx, DYxxxxx, Title or Author"}
                        "identifier")]
      (form/submit-button {:class "btn btn-default"} "Repair")))))

(defn production-repair-confirm
  [request identifier]
  (if (string/blank? identifier)
    (production-repair-form request ["Search term should not be empty"])
    (let [productions
          (cond
            (repair/production-id? identifier)
            (prod/find-archived (.substring identifier 3))
            (prod/library-signature? identifier)
            (prod/find-archived-by-library-signature identifier)
            (repair/product-number? identifier)
            (prod/find-archived-by-productnumber identifier)
            :else (let [search-term (str "%" identifier "%")]
                    (prod/find-archived-by-title-or-creator search-term)))
          identity (friend/identity request)]
      (layout/common identity
       [:h1 "Productions to repair"]
       [:table.table.table-striped
        [:thead [:tr [:th "DAM"] [:th "Title"] [:th "Creator"] [:th "Action"]]]
        [:tbody
         (for [{:keys [id title creator]} productions]
           [:tr
            [:td id] [:td title] [:td creator]
            [:td (form/form-to
                  {:class "btn-group"}
                  [:post "/production/repair"]
                  (form/hidden-field :id id)
                  (anti-forgery-field)
                  [:button.btn.btn-default
                   (layout/glyphicon "wrench") " Repair"])]])]]
       (when (empty? productions)
         [:div.alert.alert-warning {:role "alert"} "No Data to display"])))))

(defn production-repair
  [id]
  (let [production (prod/find id)]
    (repair/repair production) ;; FIXME: notify the user of a failure
    (-> (response/redirect-after-post "/")
        (assoc :flash {:message "Repair has been initiated"}))))

(defn production-set-state [id state]
  (let [production (prod/find id)
        resp (response/redirect-after-post "/")]
    (if (= state "recorded")
      ;; check if the exported production is even valid
      ;; for validation purposes pretend there is only one volume. At
      ;; this stage the number of volumes just indicates that there
      ;; should be a split into that many volumes. The volumes aren't
      ;; actually there yet
      (let [errors (prod/manifest-validate (assoc production :volumes 1))]
        (if (seq errors)
          ;; hm the exported production is not valid. Forget about the
          ;; state change.
          (-> resp
              (assoc :flash {:errors errors}))
          (do
            (prod/set-state-recorded! production)
            resp)))
      (do
        (prod/set-state! production state)
        resp))))

(defn- production-split-form-internal [id identity errors]
  (layout/common identity
   [:h1 "Split Production"]
   (when (seq errors)
     [:p [:ul.alert.alert-danger (for [e errors] [:li e])]])
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
    [:div.form-group
     (form/label {:class "col-sm-2 control-label"} :bitrate "Bitrate:")
     [:div.col-sm-2
      (form/drop-down {:class "form-control"} :bitrate [32 48 56 64 128] 56)]]
    [:div.form-group
     [:div {:class "col-sm-offset-2 col-sm-2"}
      (form/submit-button {:class "btn btn-default"} "Encode")]])))

(defn production-split-form [request id & [errors]]
  (let [identity (friend/identity request)
        production (prod/find id)] ; FIXME: the find could return nil
    (if errors
      ;; if we have errors then just show them in the form
      (production-split-form-internal id identity errors)
      ;; otherwise check if the split production is even valid. Assume
      ;; that we are going to have at least two volumes
      (let [errors (prod/manifest-validate
                    (if (prod/multi-volume? production)
                      production
                      (assoc production :volumes 2)))]
        (if (seq errors)
          ;; there are errors in the split production. No point in
          ;; showing the split diealog. Just redirect to the main view
          (-> (response/redirect-after-post "/")
              (assoc :flash {:errors errors}))
          ;; all is well. Show the split dialog
          (production-split-form-internal id identity nil))))))

(defn production-split [request id volumes sample-rate bitrate]
  (let [production (prod/find id)
        volumes (Integer/parseInt volumes) ;; FIXME: validate this
        errors (prod/manifest-validate (assoc production :volumes volumes))]
    (if (seq errors)
      (production-split-form request id errors)
      (do
        (prod/set-state-split! production volumes
                               (Integer/parseInt sample-rate) (Integer/parseInt bitrate))
        (response/redirect-after-post "/")))))

(defn url-for
  "Return the url for the given `production`"
  [{id :id}]
  (format "/production/%s" id))

(defn print-errors [errors]
  (->> errors
   (map (fn [{:keys [error line column] :as msg}]
          (if error
            (format "%s Line: %d Column: %d" error line column)
            msg)))
   (string/join \newline)
   println-str))

(defn abacus-new [f]
  (let [tempfile (:tempfile f)
        p (abacus/import-new-production tempfile)]
    (if (map? p)
      (response/created (url-for p))
      ;; an error occured
      (response/status (response/response (print-errors p)) 400))))

(defn abacus-recorded [f]
  (let [tempfile (:tempfile f)
        p (abacus/import-recorded-production tempfile)]
    (if (map? p)
      (response/response nil)
      ;; an error occured
      (response/status (response/response (print-errors p)) 400))))

(defn abacus-status [f]
  (let [tempfile (:tempfile f)
        p (abacus/import-status-request tempfile)]
    (if (map? p)
      (response/response nil)
      ;; an error occured
      (response/status (response/response (print-errors p)) 400))))

(defn abacus-metadata [f]
  (let [tempfile (:tempfile f)
        p (abacus/import-metadata-update tempfile)]
    (if (map? p)
      (response/response nil)
      ;; an error occured
      (response/status (response/response (print-errors p)) 400))))

(defn production-monitoring
  "Return a csv containing the total audio length of all productions"
  []
  (response/file-response (psm/csv (prod/find-by-state "structured"))))

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
  (let [identity (friend/identity request)]
    (->
     (layout/common identity
      [:h2
       [:div.alert.alert-danger
        "Sorry, you do not have sufficient privileges to access "
        (:uri request)]]
      [:p "Please ask an administrator for help"])
     response/response
     (response/status 401))))
