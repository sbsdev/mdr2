(ns mdr2.productions.repair
  (:require [ajax.core :as ajax]
            [clojure.set :as set]
            [mdr2.auth :as auth]
            [mdr2.ajax :refer [as-transit]]
            [mdr2.i18n :refer [tr]]
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
          offset (pagination/offset db :repair)]
      {:db (assoc-in db [:loading :repair] true)
       :http-xhrio
       (as-transit {:method          :get
                    :uri             "/api/productions"
                    :params          {:search (if (nil? search) "" search)
                                      :offset offset
                                      :limit pagination/page-size
                                      :state "archived"}
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
         (assoc-in [:productions :repair] (zipmap (map :uuid productions) productions))
         (pagination/update-next :repair next?)
         (assoc-in [:loading :repair] false)
         ;; clear all button loading states
         (update-in [:loading] dissoc :buttons)))))

(rf/reg-event-db
 ::fetch-productions-failure
 (fn [db [_ response]]
   (-> db
       (notifications/set-errors :fetch-repair-productions (get response :status-text))
       (assoc-in [:loading :repair] false))))

(rf/reg-event-fx
  ::repair-production
  (fn [{:keys [db]} [_ id]]
    {:db (notifications/set-button-state db id :repair)
     :http-xhrio (as-transit {:method          :post
                              :headers         (auth/auth-header db)
                              :uri             (str "/api/" id "/repair")
                              :on-success      [::ack-repair id]
                              :on-failure      [::ack-failure id :repair]})}))

(rf/reg-event-db
  ::ack-repair
  (fn [db [_ id]]
    (notifications/clear-button-state db id :repair)))

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
  (fn [db _] (->> db :productions :repair vals)))

(rf/reg-sub
 ::productions-sorted
 :<- [::productions]
 (fn [productions] (->> productions (sort-by :id >))))

(rf/reg-sub
  ::search
  (fn [db _] (get-in db [:search :repair])))

(rf/reg-event-fx
   ::set-search
   (fn [{:keys [db]} [_ new-search-value]]
     (cond-> {:db (assoc-in db [:search :repair] new-search-value)}
       (> (count new-search-value) 2)
       ;; if the string has more than 2 characters fetch the productions
       ;; from the server
       (assoc :dispatch-n
              (list
               ;; when searching for a new production reset the pagination
               [::pagination/reset :repair]
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
   (get-in db [:productions :repair id])))

(defn buttons [id]
  (let [roles @(rf/subscribe [::auth/user-roles])]
    (if @(rf/subscribe [::notifications/button-loading? id :repair])
       [:button.button.is-loading]
       [:button.button
        {:disabled (empty? (set/intersection #{:it :admin :studio} roles))
         :on-click (fn [e] (rf/dispatch [::repair-production id]))}
        [:span (tr [:repair])]
        [:span.icon.is-small
         [:span.material-icons "build"]]])))

(defn production-link [{:keys [id title] :as production}]
  [:a {:href (str "#/productions/" id)
       :on-click (fn [_] (rf/dispatch [::production/set-current production]))}
   title])

(defn production [id]
  (let [{:keys [uuid id title creator product_number
                library_number library_record_id
                total_time volumes depth narrator]
         :as production} @(rf/subscribe [::production id])]
    [:tr
     [:td id]
     [:td [production-link production]]
     [:td creator]
     [:td [buttons uuid]]]))

(defn page []
  (let [loading? @(rf/subscribe [::notifications/loading? :repair])
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
            [:th (tr [:action])]]]
          [:tbody
           (for [{:keys [uuid]} @(rf/subscribe [::productions-sorted])]
             ^{:key uuid} [production uuid])]]
         [pagination/pagination :repair [::fetch-productions]]])]]))
