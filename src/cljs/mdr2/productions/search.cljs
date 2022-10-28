(ns mdr2.productions.search
  (:require [mdr2.i18n :refer [tr]]
            [mdr2.pagination :as pagination]
            [re-frame.core :as rf]))

(rf/reg-sub
  ::search
  (fn [db [_ id]] (get-in db [:search id] "")))

(rf/reg-event-fx
   ::set-search
   (fn [{:keys [db]} [_ id new-search-value handler]]
     (let [length (count new-search-value)]
       (cond-> {:db (assoc-in db [:search id] new-search-value)}
         (or (= length 0) (> length 2))
         ;; do not fetch the productions from the server for very small strings,
         ;; unless the string has been reset to the empty string
         (assoc :dispatch-n
                (list
                 ;; when searching for a new production reset the pagination
                 [::pagination/reset id]
                 [handler]))))))

(defn productions-search [id handler]
  (let [get-value (fn [e] (-> e .-target .-value))
        reset!    #(rf/dispatch [::set-search id "" handler])
        save!     #(rf/dispatch [::set-search id % handler])]
    [:div.field
     [:div.control
      [:input.input {:type "text"
                     :placeholder (tr [:search])
                     :aria-label (tr [:search])
                     :value @(rf/subscribe [::search id])
                     :on-change #(save! (get-value %))
                     :on-key-down #(when (= (.-which %) 27) (reset!))}]]]))

