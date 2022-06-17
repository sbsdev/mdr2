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
    [mdr2.productions.in-production :as productions]
    [mdr2.productions.archived :as archived]
    [mdr2.productions.encoded :as encoded]
    [mdr2.productions.repair :as repair]
    [mdr2.productions.production :as production]
    [mdr2.productions.vubis :as vubis]
    [reitit.core :as reitit]
    [reitit.frontend.easy :as rfe]
    [clojure.string :as string])
  (:import goog.History))

(defn nav-link [uri title page]
  [:a.navbar-item
   {:href   uri
    :class (when (= page @(rf/subscribe [:common/page-id])) :is-active)}
   title])

(defn navbar [] 
  (r/with-let [expanded? (r/atom false)]
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
                 [nav-link "#/in-production" "Productions" :productions]
                 [nav-link "#/archived" "Archived" :archived]
                 [nav-link "#/encoded" "Encoded" :encoded]
                 [nav-link "#/repair" "Repair" :repair]
                 [nav-link "#/vubis" "Upload" :vubis]]
                [:div.navbar-end
                 [:div.navbar-item
                  (auth/user-buttons)]]]]))

(defn page []
  (if-let [page @(rf/subscribe [:common/page])]
    [:div
     [navbar]
     [page]]))

(defn navigate! [match _]
  (rf/dispatch [:common/navigate match]))

(def router
  (reitit/router
    [["/login" {:name :login
                :view #'auth/login-page}]
     ["/productions/:id" {:name :production
                          :view #'production/page
                          :controllers [{:parameters {:path [:id]}
                                         :start (fn [params] (rf/dispatch [::production/init-current (-> params :path :id)]))}]}]
     ["/in-production" {:name :productions
                        :view #'productions/productions-page
                        :controllers [{:start (fn [_] (rf/dispatch [::productions/fetch-productions]))}]}]
     ["/archived" {:name :archived
                   :view #'archived/productions-page
                   :controllers [{:start (fn [_] (rf/dispatch [::archived/fetch-productions]))}]}]
     ["/encoded" {:name :encoded
                  :view #'encoded/productions-page
                  :controllers [{:start (fn [_] (rf/dispatch [::encoded/fetch-productions]))}]}]
     ["/repair" {:name :repair
                  :view #'repair/productions-page
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
