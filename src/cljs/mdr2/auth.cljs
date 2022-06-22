(ns mdr2.auth
  (:require [ajax.core :as ajax]
            [mdr2.ajax :refer [as-transit]]
            [mdr2.events]
            [mdr2.productions.notifications :as notifications]
            [mdr2.i18n :refer [tr]]
            [mdr2.utils :as utils]
            [re-frame.core :as rf]
            [reagent.core :as r]))

(rf/reg-event-fx
  ::login
  (fn [{:keys [db]} [_ username password]]
    {:http-xhrio
     (as-transit {:method          :post
                  :uri             "/api/login"
                  :params          {:username username :password password}
                  :on-success      [::login-success]
                  :on-failure      [::login-failure]})}))

(rf/reg-event-db
 ::logout
 (fn [db [_]]
   (dissoc db :credentials)))

(rf/reg-event-fx
 ::login-success
 (fn [{:keys [db]} [_ {:keys [token user]}]]
   {:db (-> db
            (assoc-in [:credentials :token] token)
            (assoc-in [:credentials :user] user))
    :common/navigate-fx! [:in-production]}))

(rf/reg-event-db
 ::login-failure
 (fn [db [_ response]]
   (notifications/set-errors db :login (get-in response [:response :message]))))

(rf/reg-sub
 ::authenticated?
 (fn [db [_ _]]
   (-> db :credentials some?)))

(rf/reg-sub
 ::is-admin?
 (fn [db [_ _]]
   (-> db :credentials :user utils/is-admin?)))

(rf/reg-sub
 ::user
 (fn [db [_ _]]
   (-> db :credentials :user)))

(rf/reg-sub
 ::user-initials
 :<- [::user]
 (fn [user] (-> user :initials)))

(rf/reg-sub
 ::user-given-name
 :<- [::user]
 (fn [user] (-> user :givenName)))

(defn auth-header [db]
  (let [token (get-in db [:credentials :token])]
    {:Authorization (str "Token " token)}))

(defn user-buttons []
  (let [initials @(rf/subscribe [::user-initials])
        given-name @(rf/subscribe [::user-given-name])]
    [:div.buttons
     (if initials
       #_[:div.navbar-item.has-dropdown.is-hoverable
        [:a.navbar-link.has-text-weight-bold.is-arrowless initials]
        [:div.navbar-dropdown
         [:a.navbar-item {:on-click #(rf/dispatch [::logout])} (tr [:logout])]]]
       [:<>
        [:a.button.is-primary
         {:aria-label (tr [:user-initials])}
         ;; we'd like to show the initials but if they do not contain
         ;; a sensible value just show the given name
         (if (= initials "None") given-name initials)]
        [:button.button.is-light {:on-click #(rf/dispatch [::logout])} (tr [:logout])]]
       [:button.button {:on-click #(rf/dispatch [:common/navigate! :login])} (tr [:login])])]))

(defn login-page []
  (let [username (r/atom "")
        password (r/atom "")]
    (fn []
      (let [errors? @(rf/subscribe [::notifications/errors?])]
        [:section.section>div.container>div.content
         (when errors?
           [notifications/error-notification])
         [:div.field
          [:label.label
           {:for "username-field"}
           (tr [:username])]
          [:input.input
           {:type "text"
            :id "username-field"
            :aria-label (tr [:username])
            :on-change #(reset! username (-> % .-target .-value))
            :on-key-down #(case (.-which %)
                            27 (reset! username "")
                            nil)
            :value @username}]]
         [:div.field
          [:label.label
           {:for "password-field"}
           (tr [:password])]
          [:input.input
           {:type "password"
            :id "password-field"
            :aria-label (tr [:password])
            :on-change #(reset! password (-> % .-target .-value))
            :on-key-down #(case (.-which %)
                            27 (reset! password "")
                            13 (rf/dispatch [::login @username @password])
                            nil)
            :value @password}]]
         [:div.field.is-grouped
          [:div.control
           [:button.button.is-link
            {:on-click (fn [e] (rf/dispatch [::login @username @password]))}
            (tr [:login])]]]]))))
