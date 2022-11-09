(ns mdr2.productions.encoded
  (:require [ajax.core :as ajax]
            [clojure.set :as set]
            [cljs-time.format :as tf]
            [mdr2.auth :as auth]
            [mdr2.i18n :refer [tr]]
            [mdr2.ajax :refer [as-transit]]
            [mdr2.pagination :as pagination]
            [mdr2.validation :as validation]
            [mdr2.productions.production :as production]
            [mdr2.productions.input-fields :as fields]
            [mdr2.productions.notifications :as notifications]
            [mdr2.productions.search :as search]
            [re-frame.core :as rf]))

(rf/reg-event-fx
  ::fetch-productions
  (fn [{:keys [db]} [_]]
    (let [search @(rf/subscribe [::search/search :encoded])
          offset (pagination/offset db :encoded)]
      {:db (assoc-in db [:loading :encoded] true)
       :http-xhrio (as-transit
                    {:method          :get
                     :uri             "/api/productions"
                     :params          {:search search
                                       :offset offset
                                       :limit pagination/page-size
                                       :state "encoded"}
                     :on-success      [::fetch-productions-success]
                     :on-failure      [::fetch-productions-failure]})})))

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
 (fn [db [_ response]]
   (-> db
       (notifications/set-errors :fetch-encoded-productions (get response :status-text))
       (assoc-in [:loading :encoded] false))))

(rf/reg-event-fx
  ::save-production
  (fn [{:keys [db]} [_ uuid]]
    (let [{:keys [id library_signature]} (get-in db [:productions :encoded uuid])]
      {:db (notifications/set-button-state db uuid :save)
       :http-xhrio (as-transit
                    {:method          :patch
                     :headers 	      (auth/auth-header db)
                     :uri             (str "/api/productions/" id)
                     :params          {:library_signature library_signature}
                     :on-success      [::ack-save uuid]
                     :on-failure      [::ack-failure uuid :save]})})))

(rf/reg-event-fx
  ::ack-save
  (fn [{:keys [db]} [_ id]]
    (let [db (-> db
                 (update-in [:productions :encoded] dissoc id)
                 (notifications/clear-button-state id :save))
          empty? (-> db (get-in [:productions :encoded]) count (< 1))]
      (if empty?
        {:db db :dispatch [::fetch-productions]}
        {:db db}))))

(rf/reg-event-db
 ::ack-failure
 (fn [db [_ id request-type response]]
   (let [message (or (get-in response [:response :status-text])
                     (get response :status-text))]
     (-> db
         (notifications/set-errors request-type message)
         (notifications/clear-button-state id request-type)))))

(rf/reg-sub
  ::productions
  (fn [db _] (->> db :productions :encoded vals)))

(rf/reg-sub
 ::productions-sorted
 :<- [::productions]
 (fn [productions] (->> productions (sort-by :id >))))

(defn productions-filter []
  [:div.field.is-horizontal
   [:div.field-body
    [search/productions-search :encoded ::fetch-productions]]])

(rf/reg-sub
 ::production
 (fn [db [_ id]]
   (get-in db [:productions :encoded id])))

(rf/reg-sub
 ::production-valid?
 (fn [db [_ id]]
   (validation/library-signature? (get-in db [:productions :encoded id :library_signature]))))

(defn buttons [uuid]
  (let [valid? @(rf/subscribe [::production-valid? uuid])
        roles @(rf/subscribe [::auth/user-roles])]
    (if @(rf/subscribe [::notifications/button-loading? uuid :save])
      [:button.button.is-loading]
      [:button.button.has-tooltip-arrow
       {:disabled (or (not valid?)
                      (empty? (set/intersection #{:it :catalog} roles)))
        :on-click (fn [e] (rf/dispatch [::save-production uuid]))
        :data-tooltip (tr [:save])
        :aria-label (tr [:save])}
       [:span.icon {:aria-hidden true}
        [:i.material-icons "save"]]])))

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
     [:td (production/milis-to-minutes total_time)]
     [:td volumes]
     [:td depth]
     [:td narrator]
     [:td (tf/unparse (tf/formatters :date) produced_date)]
     [:td [fields/input-field :encoded uuid :library_signature validation/library-signature?]]
     [:td [buttons uuid]]]))

(defn page []
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
           (for [{:keys [uuid]} @(rf/subscribe [::productions-sorted])]
             ^{:key uuid} [production uuid])]]
         [pagination/pagination :encoded [::fetch-productions]]])]]))
