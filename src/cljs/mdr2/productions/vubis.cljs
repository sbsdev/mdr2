(ns mdr2.productions.vubis
  (:require [ajax.core :as ajax]
            [cljs-time.format :as tf]
            [mdr2.ajax :refer [as-transit]]
            [mdr2.auth :as auth]
            [mdr2.i18n :refer [tr]]
            [mdr2.productions.notifications :as notifications]
            [re-frame.core :as rf]
            [clojure.set :as set]))

(rf/reg-event-fx
  ::extract-productions
  (fn [{:keys [db]} [_ js-file-value]]
    (let [form-data (doto (js/FormData.)
                      (.append "file" js-file-value "filename.txt"))]
      {:db (-> db
               (assoc-in [:loading :vubis] true)
               (notifications/set-button-state :vubis :upload-file))
       :http-xhrio (as-transit
                    {:method          :post
                     :headers 	     (auth/auth-header db)
                     :uri             "/api/vubis/upload"
                     :body            form-data
                     :on-success      [::ack-extract-productions]
                     :on-failure      [::ack-extract-failure]})})))

(rf/reg-event-db
  ::ack-extract-productions
  (fn [db [_ productions]]
    (let [productions (->> productions
                           (map #(assoc % :uuid (str (random-uuid)))))]
      (-> db
          (assoc-in [:productions :vubis] (zipmap (map :uuid productions) productions))
          (assoc-in [:loading :vubis] false)
          (notifications/clear-button-state :vubis :upload-file)))))

(rf/reg-event-db
 ::ack-extract-failure
 (fn [db [_ response]]
   (let [message (or (get-in response [:response :status-text])
                     (get response :status-text))
         errors (get-in response [:response :errors])]
     (-> db
         (notifications/set-errors :vubis message errors)
         (assoc-in [:loading :vubis] false)
         (notifications/clear-button-state :vubis :upload-file)))))

(rf/reg-event-fx
  ::save-production
  (fn [{:keys [db]} [_ id]]
    (let [production (get-in db [:productions :vubis id])
          cleaned (dissoc production :uuid)]
      {:db (notifications/set-button-state db id :vubis)
       :http-xhrio
       (as-transit
        {:method          :post
         :headers         (auth/auth-header db)
         :uri             "/api/productions"
         :params          cleaned
         :on-success      [::ack-save id]
         :on-failure      [::ack-failure id :vubis]})})))

(rf/reg-event-db
  ::ack-save
  (fn [db [_ id]]
    (-> db
        (update-in [:productions :vubis] dissoc id)
        (notifications/clear-button-state id :vubis))))

(rf/reg-event-db
 ::ack-failure
 (fn [db [_ id request-type response]]
   (let [message (or (get-in response [:response :status-text])
                     (get response :status-text))]
     (-> db
         (notifications/set-errors request-type message)
         (notifications/clear-button-state id request-type)))))

(rf/reg-event-fx
  ::save-all-productions
  (fn [{:keys [db]} _]
    (let [ids (keys (get-in db [:productions :vubis]))]
      {:dispatch-n (map (fn [id] [::save-production id]) ids)})))

(rf/reg-event-db
  ::ignore-production
  (fn [db [_ uuid]]
    (update-in db [:productions :vubis] dissoc uuid)))

(rf/reg-event-db
  ::ignore-all-productions
  (fn [db _]
    (update-in db [:productions] dissoc :vubis)))

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
 ::upload-file
 (fn [db _] (get-in db [:upload :vubis])))

(rf/reg-event-db
  ::set-upload-file
  (fn [db [_ file]] (assoc-in db [:upload :vubis] file)))

(defn- file-input []
  (let [get-value (fn [e] (-> e .-target .-files (aget 0)))
        save!     #(rf/dispatch [::set-upload-file %])
        file      @(rf/subscribe [::upload-file])]
    [:p.control
     [:div.file.has-name
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
  (let [klass (when @(rf/subscribe [::notifications/button-loading? :vubis :upload-file]) "is-loading")
        roles @(rf/subscribe [::auth/user-roles])
        file @(rf/subscribe [::upload-file])]
    [:div.field
     [:label.label (tr [:upload-vubis])]
     [file-input]
     [:p.control
      [:button.button
       {:disabled (or (nil? file) (empty? (set/intersection #{:it :admin} roles)))
        :class klass
        :on-click (fn [e] (rf/dispatch [::extract-productions file]))}
       [:span (tr [:upload])]
       [:span.icon.is-small
        [:span.material-icons "upload_file"]]]]]))

(defn buttons [id]
  (let [roles @(rf/subscribe [::auth/user-roles])]
    (if @(rf/subscribe [::notifications/button-loading? id :save])
      [:button.button.is-loading]
      [:div.field.has-addons
       [:p.control
        [:button.button.is-danger
         {:disabled (empty? (set/intersection #{:it :admin} roles))
          :on-click (fn [e] (rf/dispatch [::ignore-production id]))}
         #_[:span.is-sr-only (tr [:ignore])]
         [:span.icon
          [:span.material-icons "cancel"]]]]
       [:p.control
        [:button.button.is-success
         {:disabled (empty? (set/intersection #{:it :admin} roles))
          :on-click (fn [e] (rf/dispatch [::save-production id]))}
         #_[:span (tr [:save])]
         [:span.icon
          [:span.material-icons "done"]]]]])))

(defn- production [id]
  (let [{:keys [title creator source description
                library_number source_publisher source_date]
         :as production} @(rf/subscribe [::production id])]
    [:tr
     [:td title]
     [:td creator]
     [:td source]
     [:td description]
     [:td library_number]
     [:td source_publisher]
     [:td (tf/unparse (tf/formatters :date) source_date)]
     [:td [buttons id]]]))

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
      [:th (tr [:source_date])]
      [:th (tr [:action])]]]
    [:tbody
     (for [{:keys [uuid]} @(rf/subscribe [::productions-sorted])]
       ^{:key uuid} [production uuid])]]
   [:div.buttons.is-right
    [:button.button.is-success
     {:on-click (fn [e] (rf/dispatch [::save-all-productions]))}
     [:span (tr [:approve-all])]
     [:span.icon.is-small
      [:span.material-icons "done"]]]
    [:button.button.is-danger
     {:on-click (fn [e] (rf/dispatch [::ignore-all-productions]))}
     [:span (tr [:cancel])]
     [:span.icon.is-small
      [:span.material-icons "cancel"]]]]])

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
