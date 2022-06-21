(ns mdr2.productions.archived
  (:require [ajax.core :as ajax]
            [mdr2.auth :as auth]
            [cljs-time.format :as tf]
            [mdr2.ajax :refer [as-transit]]
            [mdr2.i18n :refer [tr]]
            [mdr2.pagination :as pagination]
            [mdr2.productions.production :as production]
            [mdr2.productions.notifications :as notifications]
            [re-frame.core :as rf]))

(rf/reg-event-fx
  ::fetch-productions
  (fn [{:keys [db]} [_]]
    (let [search @(rf/subscribe [::search])
          offset (pagination/offset db :archived)]
      {:db (assoc-in db [:loading :archived] true)
       :http-xhrio
       (as-transit {:method          :get
                    :uri             "/api/productions"
                    :params          {:search (if (nil? search) "" search)
                                      :offset offset
                                      :limit pagination/page-size
                                      :state "archived"}
                    :on-success      [::fetch-productions-success]
                    :on-failure      [::fetch-productions-failure :fetch-archived-productions]})})))

(rf/reg-event-db
 ::fetch-productions-success
 (fn [db [_ productions]]
   (let [productions (->> productions
                    (map #(assoc % :uuid (str (random-uuid)))))
         next? (-> productions count (= pagination/page-size))]
     (-> db
         (assoc-in [:productions :archived] (zipmap (map :uuid productions) productions))
         (pagination/update-next :archived next?)
         (assoc-in [:loading :archived] false)
         ;; clear all button loading states
         (update-in [:loading] dissoc :buttons)))))

(rf/reg-event-db
 ::fetch-productions-failure
 (fn [db [_ request-type response]]
   (-> db
       (assoc-in [:errors request-type] (get response :status-text))
       (assoc-in [:loading :archived] false))))

(rf/reg-sub
  ::productions
  (fn [db _] (->> db :productions :archived vals)))

(rf/reg-sub
 ::productions-sorted
 :<- [::productions]
 (fn [productions] (->> productions (sort-by :id ))))

(rf/reg-sub
  ::search
  (fn [db _] (get-in db [:search :archived])))

(rf/reg-event-fx
   ::set-search
   (fn [{:keys [db]} [_ new-search-value]]
     (cond-> {:db (assoc-in db [:search :archived] new-search-value)}
       (> (count new-search-value) 2)
       ;; if the string has more than 2 characters fetch the productions
       ;; from the server
       (assoc :dispatch-n
              (list
               ;; when searching for a new production reset the pagination
               [::pagination/reset :archived]
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
   (get-in db [:productions :archived id])))

(defn production-link [{:keys [id title] :as production}]
  [:a {:href (str "#/productions/" id)
       :on-click (fn [_] (rf/dispatch [::production/set-current production]))}
   title])


(defn production [id]
  (let [{:keys [uuid id title creator product_number library_number total_time volumes depth narrator produced_date] :as production} @(rf/subscribe [::production id])]
    [:tr
     [:td id]
     [:td [production-link production]]
     [:td creator]
     [:td product_number]
     [:td library_number]
     [:td (if total_time
            (quot total_time (* 1000 60)) "")] ; in minutes
     [:td volumes]
     [:td depth]
     [:td narrator]
     [:td (tf/unparse (tf/formatters :date) produced_date)]]))

(defn page []
  (let [loading? @(rf/subscribe [::notifications/loading? :archived])
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
            [:th (tr [:dam])]
            [:th (tr [:title])]
            [:th (tr [:creator])]
            [:th (tr [:product_number])]
            [:th (tr [:library_number])]
            [:th (tr [:total_time])]
            [:th (tr [:volumes])]
            [:th (tr [:depth])]
            [:th (tr [:narrator])]
            [:th (tr [:produced_date])]]]
          [:tbody
           (for [{:keys [uuid]} @(rf/subscribe [::productions-sorted])]
             ^{:key uuid} [production uuid])]]
         [pagination/pagination :archived [::fetch-productions]]])]]))
