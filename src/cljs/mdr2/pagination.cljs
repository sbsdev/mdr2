(ns mdr2.pagination
  (:require [mdr2.i18n :refer [tr]]
            [re-frame.core :as rf]))

(def page-size 100)

(defn offset [db id]
  (get-in db [:pagination id :offset] 0))

(defn update-next [db id next?]
  (assoc-in db [:pagination id :next?] next?))

(defn- update-prev [db id]
  (let [prev? (-> db (offset id) pos?)]
    (assoc-in db [:pagination id :prev?] prev?)))

(defn- has-next? [db id]
  (get-in db [:pagination id :next?]))

(defn- has-previous? [db id]
  (get-in db [:pagination id :prev?]))

(rf/reg-event-db
 ::reset
 (fn [db [_ id]]
   (assoc-in db [:pagination id :offset] 0)))

(rf/reg-event-fx
 ::next-page
 (fn [{:keys [db]} [_ id fetch-event]]
   (let [inc-offset (->
                     (fn [offset window-size] (+ offset window-size))
                     (fnil 0))
         window-size (-> db (get-in [:productions id]) count)]
     (when (has-next? db id)
       {:db (-> db
                (update-in [:pagination id :offset] inc-offset window-size)
                (update-prev id))
        :dispatch fetch-event}))))

(rf/reg-event-fx
 ::previous-page
 (fn [{:keys [db]} [_ id fetch-event]]
   (let [dec-offset (->
                     (fn [offset] (-> offset (- page-size) (max 0)))
                     (fnil 0))]
     (when (has-previous? db id)
       {:db (-> db
                (update-in [:pagination id :offset] dec-offset)
                (update-prev id))
        :dispatch fetch-event}))))

(rf/reg-sub
 ::has-next?
 (fn [db [_ id]]
   (has-next? db id)))

(rf/reg-sub
 ::has-previous?
 (fn [db [_ id]]
   (has-previous? db id)))

(defn pagination [id event]
  (let [has-previous? @(rf/subscribe [::has-previous? id])
        has-next? @(rf/subscribe [::has-next? id])]
    [:nav.pagination.is-right {:role "navigation" :aria-label "pagination"}
     [:button.pagination-previous
      {:disabled (not has-previous?)
       :on-click (fn [e] (rf/dispatch [::previous-page id event]))}
      (tr [:previous])]
     [:button.pagination-next
      {:disabled (not has-next?)
       :on-click (fn [e] (rf/dispatch [::next-page id event]))}
      (tr [:next])]
     ;; we have to add an empty pagination list to make the rest of the pagination nav work
     [:ul.pagination-list]]))

