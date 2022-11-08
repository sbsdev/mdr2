(ns mdr2.productions.forms
  (:require [fork.re-frame :as fork]
            [re-frame.core :as rf]))

(rf/reg-event-db
 ::ack-error
 (fn [db [_ path]]
   (fork/set-server-message db path nil)))

(defn error-notification [message path]
  [:div.notification.is-danger
   [:button.delete
    {:type "button" ;; make sure the button doesn't trigger an event in the form
     :on-click (fn [e] (rf/dispatch [::ack-error path]))}]
   message])
