(ns mdr2.productions.production
  (:require [ajax.core :as ajax]
            [mdr2.ajax :refer [as-transit]]
            [cljs-time.format :as tf]
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

(rf/reg-event-db
  ::clear-current
  (fn [db [_]]
    (dissoc db :current-production)))

(rf/reg-event-fx
  ::fetch-current
  (fn [_ [_ id]]
    {:http-xhrio
     (as-transit {:method          :get
                  :uri             (str "/api/productions/" id)
                  :on-success      [::set-current]})}))

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
       [:tr [:th (tr [:source_publisher])] [:td source_publisher]]
       [:tr [:th (tr [:state])] [:td state]]]]])

(defn milis-to-minutes [milis]
  (when milis (quot milis (* 1000 60))))

(defn humanize-milis [milis]
  (tr [:total-time-human] [(milis-to-minutes milis)]))

(defn details [production]
  [:div.block
   [:table.table.is-striped
    [:tbody
     (for [k (remove #{:title :creator :state :source_publisher} (keys production)) #_[:date :modified-at :language]
           :let [v (case k
                     #_:spelling #_(state/mapping (get production k))
                     :total_time (humanize-milis (get production k))
                     (:produced_date :date :source_date :revision_date) (if-let [raw (get production k)] (tf/unparse (tf/formatters :date) raw) "")
                     (get production k))]
           :when (not (string/blank? v))]
       ^{:key k}
       [:tr [:th (tr [k])] [:td v]])]]])

(defn page []
  (let [production @(rf/subscribe [::current])]
    [:section.section>div.container>div.content
     [summary production]
     [details production]]))


