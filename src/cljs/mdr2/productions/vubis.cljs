(ns mdr2.productions.vubis
  (:require [ajax.core :as ajax]
            [cljs-time.format :as tf]
            [clojure.string :as string]
            [mdr2.ajax :refer [as-transit]]
            [mdr2.auth :as auth]
            [mdr2.i18n :refer [tr]]
            [mdr2.productions.notifications :as notifications]
            [re-frame.core :as rf]))

(rf/reg-event-fx
  ::vubis-file
  (fn [{:keys [db]} [_ js-file-value]]
    (let [form-data (doto (js/FormData.)
                      (.append "file" js-file-value "filename.txt"))]
      {:db (-> db
               (assoc-in [:loading :vubis] true)
               (notifications/set-button-state :vubis :vubis-file))
       :http-xhrio (as-transit
                    {:method          :post
                     :headers 	     (auth/auth-header db)
                     :uri             "/api/vubis/upload"
                     :body            form-data
                     :on-success      [::ack-vubis-file]
                     :on-failure      [::ack-failure]})})))

(rf/reg-event-db
  ::ack-vubis-file
  (fn [db [_ productions]]
    (let [productions (->> productions
                           (map #(assoc % :uuid (str (random-uuid)))))]
      (-> db
          (assoc-in [:productions :vubis] (zipmap (map :uuid productions) productions))
          (assoc-in [:loading :vubis] false)
          (notifications/clear-button-state :vubis :vubis-file)))))

(rf/reg-event-db
 ::ack-failure
 (fn [db [_ response]]
   (-> db
       (assoc-in [:errors :version] (or (get-in response [:response :status-text])
                                        (get response :status-text)))
       (notifications/clear-button-state :vubis :vubis-file))))

(rf/reg-sub
 ::productions
 (fn [db _] (->> db :productions :vubis vals)))

(rf/reg-sub
 ::productions-sorted
 :<- [::productions]
 (fn [productions] (->> productions (sort-by :title))))

(rf/reg-sub
 ::has-productions?
 :<- [::productions]
 (fn [productions] (->> productions seq some?)))

(rf/reg-sub
 ::production
 (fn [db [_ id]] (get-in db [:productions :vubis id])))

(rf/reg-sub
 ::vubis-file
 (fn [db _] (-> db :vubis-file)))

(rf/reg-event-db
  ::set-vubis-file
  (fn [db [_ file]] (assoc db :vubis-file file)))

(defn- file-input []
  (let [get-value (fn [e] (-> e .-target .-files (aget 0)))
        save!     #(rf/dispatch [::set-vubis-file %])
        file      @(rf/subscribe [::vubis-file])]
    [:p.control
     [:div.file.has-name.is-fullwidth
      [:label.file-label
       [:input.file-input
        {:type "file"
         :accept ".xml"
         :files file
         :on-change #(save! (get-value %))}]
       [:span.file-cta
        [:span.file-label (tr [:choose-vubis])]]
       [:span.file-name (if file (.-name file) (tr [:no-file]))]]]]))

(defn- file-upload []
  (let [klass (when @(rf/subscribe [::notifications/button-loading? :vubis :vubis-file]) "is-loading")
        admin? @(rf/subscribe [::auth/is-admin?])
        file @(rf/subscribe [::vubis-file])]
    [:div.field.is-grouped
     [file-input]
     [:p.control
      [:button.button
       {:disabled (or (nil? file) (not admin?))
        :class klass
        :on-click (fn [e] (rf/dispatch [::vubis-file file]))}
       [:span.icon.is-small
        [:span.material-icons "upload_file"]]]]]))

(defn- production [id]
  (let [{:keys [title creator source description
                library_number source_publisher source_date]
         :as production} @(rf/subscribe [::production id])]
    (println title)
    (println id)
    [:tr
     [:td title]
     [:td creator]
     [:td source]
     [:td description]
     [:td library_number]
     [:td source_publisher]
     [:td (tf/unparse (tf/formatters :date) source_date)]]))

(defn- productions []
  [:<>
   [:table.table.is-striped
    [:thead
     [:tr
      [:th (tr [:title])]
      [:th (tr [:creator])]
      [:th (tr [:source])]
      [:th (tr [:description])]
      [:th (tr [:library_number])]
      [:th (tr [:source_publisher])]
      [:th (tr [:source_date])]]]
    [:tbody
     (for [{:keys [uuid]} @(rf/subscribe [::productions-sorted])]
       ^{:key uuid} [production uuid])]]
   [:div.buttons.has-addons.is-right
    [:button.button
     {:on-click (fn [e] (rf/dispatch ::confirm-all))}
     [:span (tr [:approve-all])]
     [:span.icon.is-small
      [:span.material-icons "done"]]]]])

(defn page []
  (let [loading? @(rf/subscribe [::notifications/loading? :vubis])
        errors? @(rf/subscribe [::notifications/errors?])
        confirming? @(rf/subscribe [::has-productions?])]
    [:section.section>div.container>div.content
     (cond
        errors? [notifications/error-notification]
        loading? [notifications/loading-spinner]
        confirming? [productions]
        :else [file-upload])]))
