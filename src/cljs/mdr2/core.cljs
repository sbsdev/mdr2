(ns mdr2.core
  (:require
    [day8.re-frame.http-fx]
    [reagent.dom :as rdom]
    [reagent.core :as r]
    [re-frame.core :as rf]
    [goog.events :as events]
    [goog.history.EventType :as HistoryEventType]
    [markdown.core :refer [md->html]]
    [mdr2.ajax :as ajax]
    [mdr2.events]
    [mdr2.auth :as auth]
    [mdr2.productions.in-production :as in-production]
    [mdr2.productions.archived :as archived]
    [mdr2.productions.encoded :as encoded]
    [mdr2.productions.repair :as repair]
    [mdr2.productions.production :as production]
    [mdr2.productions.vubis :as vubis]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [clojure.string :as string]
    [clojure.set :as set])
  (:import goog.History))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href   uri
    :class (when (= page @(rf/subscribe [:common/page-id])) :is-active)}
   title])

(defn navbar []
  (r/with-let [expanded? (r/atom false)]
    (let [roles @(rf/subscribe [::auth/user-roles])]
          [:nav.navbar.is-info>div.container
           [:div.navbar-brand
            [:a.navbar-item {:style {:font-weight :bold}} "Madras 2"]
            [:span.navbar-burger.burger
             {:data-target :nav-menu
              :on-click #(swap! expanded? not)
              :class (when @expanded? :is-active)}
             [:span][:span][:span]]]
           [:div#nav-menu.navbar-menu
            {:class (when @expanded? :is-active)}
            [:div.navbar-start
             [nav-link "#/" "Productions" :in-production]
             [nav-link "#/archived" "Archived" :archived]
             (when (seq (set/intersection #{:it :catalog} roles))
                 [nav-link "#/encoded" "Encoded" :encoded])
             (when (seq (set/intersection #{:it :admin :studio} roles))
                 [nav-link "#/repair" "Repair" :repair])
             (when (seq (set/intersection #{:it :admin} roles))
               [nav-link "#/vubis" "Upload" :vubis])]
            [:div.navbar-end
             [:div.navbar-item
              (auth/user-buttons)]]]])))

(defn page []
  (if-let [page @(rf/subscribe [:common/page])]
    [:div
     [navbar]
     [page]]))

(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))

(def router
  (reitit/router
   [["/" {:name :in-production
          :view #'in-production/page
          :controllers [{:start (fn [_] (rf/dispatch [::in-production/fetch-productions]))}]}]
    ["/login" {:name :login
                :view #'auth/login-page}]
     ["/productions/:id" {:name :production
                          :view #'production/page
                          :controllers [{:parameters {:path [:id]}
                                         :start (fn [params] (rf/dispatch [::production/init-current (-> params :path :id)]))
                                         :stop (fn [_] (rf/dispatch [::production/clear-current]))}]}]
     ["/productions/:id/upload" {:name :production-upload
                                 :view #'in-production/upload-page
                                 :controllers [{:stop (fn [_]
                                                        (rf/dispatch [::production/clear-current])
                                                        (rf/dispatch [::in-production/clear-upload-file]))}]}]
    ["/productions/:id/split" {:name :production-split
                               :view #'in-production/split-page
                               :controllers [{:stop (fn [_]
                                                      (rf/dispatch [::production/clear-current]))}]}]
     ["/archived" {:name :archived
                   :view #'archived/page
                   :controllers [{:start (fn [_] (rf/dispatch [::archived/fetch-productions]))}]}]
     ["/encoded" {:name :encoded
                  :view #'encoded/page
                  :controllers [{:start (fn [_] (rf/dispatch [::encoded/fetch-productions]))}]}]
     ["/repair" {:name :repair
                  :view #'repair/page
                 :controllers [{:start (fn [_] (rf/dispatch [::repair/fetch-productions]))}]}]
     ["/vubis" {:name :vubis
                 :view #'vubis/page}]
]))

(defn start-router! []
  (rfe/start!
    router
    navigate!
    {}))

;; -------------------------
;; Initialize app
(defn ^:dev/after-load mount-components []
  (rf/clear-subscription-cache!)
  (rdom/render [#'page] (.getElementById js/document "app")))

(defn init! []
  (start-router!)
  (ajax/load-interceptors!)
  (mount-components))
