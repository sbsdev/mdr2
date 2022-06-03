(ns mdr2.productions.input-fields
  (:require [mdr2.i18n :refer [tr]]
            [re-frame.core :as rf]))

(rf/reg-sub
 ::production-field
 (fn [db [_ page id field-id]]
   (get-in db [:productions page id field-id])))

(rf/reg-event-db
 ::set-production-field
 (fn [db [_ page id field-id value]]
   (assoc-in db [:productions page id field-id] value)))

(defn input-field [page id field-id validator]
  (let [initial-value @(rf/subscribe [::production-field page id field-id])
        get-value (fn [e] (-> e .-target .-value))
        reset! #(rf/dispatch [::set-production-field page id field-id initial-value])
        save! #(rf/dispatch [::set-production-field page id field-id %])]
    (fn []
      (let [value @(rf/subscribe [::production-field page id field-id])
            valid? (validator value)
            changed? (not= initial-value value)
            klass (list (cond (not valid?) "is-danger"
                              changed? "is-warning"))]
        [:div.field
         [:input.input {:type "text"
                        :aria-label (tr [field-id])
                        :class klass
                        :value value
                        :on-change #(save! (get-value %))
                        :on-key-down #(when (= (.-which %) 27) (reset!))}]
         (when-not valid?
           [:p.help.is-danger (tr [:input-not-valid])])]))))

(defn disabled-field [value]
  [:div.field
   [:input.input {:type "text" :value value :disabled "disabled"}]])
