(ns mdr2.productions.encoded
  (:require [ajax.core :as ajax]
            [cljs-time.format :as tf]
            [mdr2.auth :as auth]
            [mdr2.i18n :refer [tr]]
            [mdr2.ajax :refer [as-transit]]
            [mdr2.pagination :as pagination]
            [mdr2.validation :as validation]
            [mdr2.productions.production :as production]
            [mdr2.productions.input-fields :as fields]
            [mdr2.productions.notifications :as notifications]
            [re-frame.core :as rf]))

(rf/reg-event-fx
  ::fetch-productions
  (fn [{:keys [db]} [_]]
    (let [search @(rf/subscribe [::search])
          offset (pagination/offset db :encoded)]
      {:db (assoc-in db [:loading :encoded] true)
       :http-xhrio (as-transit
                    {:method          :get
                     :uri             "/api/productions"
                     :params          {:search (if (nil? search) "" search)
                                       :offset offset
                                       :limit pagination/page-size
                                       :state "encoded"}
                     :on-success      [::fetch-productions-success]
                     :on-failure      [::fetch-productions-failure :fetch-encoded-productions]})})))

(rf/reg-event-db
 ::fetch-productions-success
 (fn [db [_ productions]]
   (let [productions (->> productions
                          (map #(assoc % :uuid (str (random-uuid))))
                          (map #(assoc % :library_signature "")))
         next? (-> productions count (= pagination/page-size))]
     (-> db
         (assoc-in [:productions :encoded] (zipmap (map :uuid productions) productions))
         (pagination/update-next :encoded next?)
         (assoc-in [:loading :encoded] false)
         ;; clear all button loading states
         (update-in [:loading] dissoc :buttons)))))

(rf/reg-event-db
 ::fetch-productions-failure
 (fn [db [_ request-type response]]
   (-> db
       (assoc-in [:errors request-type] (get response :status-text))
       (assoc-in [:loading :encoded] false))))

(rf/reg-event-fx
  ::save-production
  (fn [{:keys [db]} [_ id]]
    (let [production (get-in db [:productions :encoded id])
          cleaned (-> production
                      (select-keys [:untranslated :uncontracted :contracted :type :homograph-disambiguation]))]
      {:db (notifications/set-button-state db id :save)
       :http-xhrio (as-transit
                    {:method          :put
                     :headers 	     (auth/auth-header db)
                     :uri             (str "/api/productions")
                     :params          cleaned
                     :on-success      [::ack-save id]
                     :on-failure      [::ack-failure id :save]})})))

(rf/reg-event-db
  ::ack-save
  (fn [db [_ id]]
    (notifications/clear-button-state db id :save)))

(rf/reg-event-db
 ::ack-failure
 (fn [db [_ id request-type response]]
   (-> db
       (assoc-in [:errors request-type] (or (get-in response [:response :status-text])
                                            (get response :status-text)))
       (notifications/clear-button-state id request-type))))

(rf/reg-sub
  ::productions
  (fn [db _]
    (->> db :productions :encoded vals (sort-by :id >))))

(rf/reg-sub
  ::search
  (fn [db _]
    (get-in db [:search :encoded])))

(rf/reg-event-fx
   ::set-search
   (fn [{:keys [db]} [_ new-search-value]]
     (cond-> {:db (assoc-in db [:search :encoded] new-search-value)}
       (> (count new-search-value) 2)
       ;; if the string has more than 2 characters fetch the productions
       ;; from the server
       (assoc :dispatch-n
              (list
               ;; when searching for a new production reset the pagination
               [::pagination/reset :encoded]
               [::fetch-productions])))))

(defn productions-search []
  (let [get-value (fn [e] (-> e .-target .-value))
        reset!    #(rf/dispatch [::set-search ""])
        save!     #(rf/dispatch [::set-search %])]
    [:div.field
     [:div.control
      [:input.input {:type "text"
                     :placeholder (tr [:search])
                     :aria-label (tr [:search])
                     :value @(rf/subscribe [::search])
                     :on-change #(save! (get-value %))
                     :on-key-down #(when (= (.-which %) 27) (reset!))}]]]))

(defn productions-filter []
  [:div.field.is-horizontal
   [:div.field-body
    [productions-search]]])

(rf/reg-sub
 ::production
 (fn [db [_ id]]
   (get-in db [:productions :encoded id])))

(rf/reg-sub
 ::valid?
 (fn [db [_ id]]
   (validation/library-signature? (get-in db [:productions :encoded id :library_signature]))))

(defn buttons [id]
  (let [valid? @(rf/subscribe [::valid? id])
        admin? @(rf/subscribe [::auth/is-admin?])]
    [:div.buttons.has-addons
     (if @(rf/subscribe [::notifications/button-loading? id :save])
       [:button.button.is-loading]
       [:button.button
        {:disabled (not (and valid? admin?))
         :on-click (fn [e] (rf/dispatch [::save-production id]))}
        [:span.material-icons "save"]])]))

(defn production-link [{:keys [id title] :as production}]
  [:a {:href (str "#/productions/" id)
       :on-click (fn [_] (rf/dispatch [::production/set-current production]))}
   title])

(defn production [id]
  (let [{:keys [uuid id title creator product_number
                library_number library_record_id
                total_time volumes depth narrator produced_date]
         :as production} @(rf/subscribe [::production id])]
    [:tr
     [:td [production-link production]]
     [:td creator]
     [:td product_number]
     [:td library_number]
     [:td library_record_id]
     [:td (if total_time
            (quot total_time (* 1000 60)) "")] ; in minutes
     [:td volumes]
     [:td depth]
     [:td narrator]
     [:td (tf/unparse (tf/formatters :date) produced_date)]
     [:td [fields/input-field :encoded uuid :library_signature validation/library-signature?]]
     [:td [buttons uuid]]]))

(defn productions-page []
  (let [loading? @(rf/subscribe [::notifications/loading? :encoded])
        errors? @(rf/subscribe [::notifications/errors?])]
    [:section.section>div.container>div.content
     [:<>
      [productions-filter]
      (cond
        errors? [notifications/error-notification]
        loading? [notifications/loading-spinner]
        :else
        [:<>
         [:table.table.is-striped
          [:thead
           [:tr
            [:th (tr [:title])]
            [:th (tr [:creator])]
            [:th (tr [:product_number])]
            [:th (tr [:library_number])]
            [:th (tr [:library_record_id])]
            [:th (tr [:total_time])]
            [:th (tr [:volumes])]
            [:th (tr [:depth])]
            [:th (tr [:narrator])]
            [:th (tr [:produced_date])]
            [:th (tr [:library_signature])]
            [:th (tr [:action])]]]
          [:tbody
           (for [{:keys [uuid]} @(rf/subscribe [::productions])]
             ^{:key uuid} [production uuid])]]
         [pagination/pagination :encoded [::fetch-productions]]])]]))