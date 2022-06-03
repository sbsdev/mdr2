(ns mdr2.productions.production
  (:require [ajax.core :as ajax]
            [clojure.string :as string]
            [mdr2.i18n :refer [tr]]
            [re-frame.core :as rf]))

(rf/reg-sub
 ::current
 (fn [db _]
   (-> db :current-production)))

(rf/reg-event-db
  ::set-current
  (fn [db [_ production]]
    (assoc db :current-production production)))

(rf/reg-event-fx
  ::fetch-current
  (fn [_ [_ id]]
    {:http-xhrio {:method          :get
                  :uri             (str "/api/productions/" id)
                  :response-format (ajax/json-response-format {:keywords? true})
                  :on-success      [::set-current]}}))

(rf/reg-event-fx
  ::init-current
  (fn [{:keys [db]} [_ id]]
    {:dispatch [::fetch-current id]}))


(defn summary [{:keys [title creator source_publisher state]}]
  [:div.block
     [:table.table
      [:tbody
       [:tr [:th {:width 200} (tr [:title])] [:td title]]
       [:tr [:th (tr [:creator])] [:td creator]]
       [:tr [:th (tr [:source-publisher])] [:td source_publisher]]
       [:tr [:th (tr [:state])] [:td state]]]]])

(defn details [production]
  [:div.block
   [:table.table.is-striped
    [:tbody
     (for [k (remove #{:title :creator :state :source_publisher} (keys production)) #_[:date :modified-at :language]
           :let [v (case k
                     #_:spelling #_(state/mapping (get production k))
                     (get production k))]
           :when (not (string/blank? v))]
       ^{:key k}
       [:tr [:th (tr [k])] [:td v]])]]])

(defn page []
  (let [production @(rf/subscribe [::current])]
    [:section.section>div.container>div.content
     [summary production]
     [details production]]))


