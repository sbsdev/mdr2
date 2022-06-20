(ns mdr2.productions.in-production
  (:require [ajax.core :as ajax]
            [mdr2.auth :as auth]
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
          offset (pagination/offset db :in-production)]
      {:db (assoc-in db [:loading :in-production] true)
       :http-xhrio
       (as-transit {:method          :get
                    :uri             "/api/productions"
                    :params          {:search (if (nil? search) "" search)
                                      :offset offset
                                      :limit pagination/page-size}
                    :on-success      [::fetch-productions-success]
                    :on-failure      [::fetch-productions-failure :fetch-in-production-productions]})})))

(rf/reg-event-db
 ::fetch-productions-success
 (fn [db [_ productions]]
   (let [productions (->> productions
                    (map #(assoc % :uuid (str (random-uuid)))))
         next? (-> productions count (= pagination/page-size))]
     (-> db
         (assoc-in [:productions :in-production] (zipmap (map :uuid productions) productions))
         (pagination/update-next :in-production next?)
         (assoc-in [:loading :in-production] false)
         ;; clear all button loading states
         (update-in [:loading] dissoc :buttons)))))

(rf/reg-event-db
 ::fetch-productions-failure
 (fn [db [_ request-type response]]
   (-> db
       (assoc-in [:errors request-type] (get response :status-text))
       (assoc-in [:loading :in-production] false))))

(rf/reg-event-fx
  ::delete-production
  (fn [{:keys [db]} [_ id]]
    (let [production (get-in db [:productions :in-production id])]
      {:db (notifications/set-button-state db id :delete)
       :http-xhrio
       (as-transit {:method          :delete
                    :headers 	     (auth/auth-header db)
                    :uri             (str "/api/productions/" (:id production))
                    :on-success      [::ack-delete id]
                    :on-failure      [::ack-failure id :delete]
                    })})))

(rf/reg-event-db
  ::ack-save
  (fn [db [_ id]]
    (notifications/clear-button-state db id :save)))

(rf/reg-event-fx
  ::ack-delete
  (fn [{:keys [db]} [_ id]]
    (let [db (-> db
                 (update-in [:productions :in-production] dissoc id)
                 (notifications/clear-button-state id :delete))
          empty? (-> db (get-in [:productions :in-production]) count (< 1))]
      (if empty?
        {:db db :dispatch [::fetch-productions]}
        {:db db}))))

(rf/reg-event-db
 ::ack-failure
 (fn [db [_ id request-type response]]
   (-> db
       (assoc-in [:errors request-type] (or (get-in response [:response :status-text])
                                            (get response :status-text)))
       (notifications/clear-button-state id request-type))))

(rf/reg-sub
 ::productions
 (fn [db _] (->> db :productions :in-production vals)))

(rf/reg-sub
 ::productions-sorted
 :<- [::productions]
 (fn [productions] (->> productions (sort-by :id >))))

(rf/reg-sub
  ::search
  (fn [db _] (get-in db [:search :in-production])))

(rf/reg-event-fx
   ::set-search
   (fn [{:keys [db]} [_ new-search-value]]
     (cond-> {:db (assoc-in db [:search :in-production] new-search-value)}
       (> (count new-search-value) 2)
       ;; if the string has more than 2 characters fetch the productions
       ;; from the server
       (assoc :dispatch-n
              (list
               ;; when searching for a new production reset the pagination
               [::pagination/reset :in-production]
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
   (get-in db [:productions :in-production id])))

(defn buttons [{:keys [uuid id state]}]
  (let [admin? @(rf/subscribe [::auth/is-admin?])]
    [:div.buttons.has-addons
     [:a.button
      {:href (str "/api/productions/" id "/xml")
       :download (str id ".xml")
       ;; allow dtbook download only for state new and structured
       :disabled (not (#{"new" "structured"} state))}
      [:span.icon.is-small
       [:span.material-icons "file_download"]]]
     (if @(rf/subscribe [::notifications/button-loading? uuid :upload])
       [:button.button.is-loading]
       [:button.button
        {:on-click (fn [e] (rf/dispatch [::upload-production uuid]))}
        [:span.icon.is-small
         [:span.material-icons "file_upload"]]])
     (if @(rf/subscribe [::notifications/button-loading? uuid :delete])
       [:button.button.is-danger.is-loading]
       [:button.button.is-danger
        {:disabled (not admin?)
         :on-click (fn [e] (rf/dispatch [::delete-production uuid]))}
        [:span.icon.is-small
         [:span.material-icons "delete"]]])]))

(defn production-link [{:keys [id title] :as production}]
  [:a {:href (str "#/productions/" id)
       :on-click (fn [_] (rf/dispatch [::production/set-current production]))}
   title])


(defn production [id]
  (let [{:keys [uuid id title type state] :as production} @(rf/subscribe [::production id])]
    [:tr
     [:td id]
     [:td [production-link production]]
     [:td type]
     [:td state]
     [:td {:width "11%"} [buttons production]]]))

(defn productions-page []
  (let [loading? @(rf/subscribe [::notifications/loading? :in-production])
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
            [:th (tr [:type])]
            [:th (tr [:state])]
            [:th (tr [:action])]]]
          [:tbody
           (for [{:keys [uuid]} @(rf/subscribe [::productions-sorted])]
             ^{:key uuid} [production uuid])]]
         [pagination/pagination :in-production [::fetch-productions]]])]]))
