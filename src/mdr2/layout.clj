(ns mdr2.layout
  (:require [hiccup.page :refer [html5 include-css]]))

(defn navbar [user]
  [:div.navbar.navbar-default {:role "navigation"}
   [:div.container-fluid
    [:div.navbar-header
     [:button.navbar-toggle
      {:type "button"
       :data-toggle "collapse" :data-target "#navbar-collapse-target"}
      [:span.sr-only "Toggle navigation"]
      [:span.icon-bar]
      [:span.icon-bar]
      [:span.icon-bar]]
     [:a.navbar-brand {:href "/"} "Madras 2"]]
    [:div.collapse.navbar-collapse
     {:id "navbar-collapse-target"}
     [:ul.nav.navbar-nav.navbar-right
      (if user
        (list
         [:li [:a "Logged in as " [:b (:username user)]]]
         [:li [:a {:href "/logout"} "Logout"]])
        [:li [:a {:href "/login"} "Login"]])]]]])

(defn common [user & body]
  (html5
    [:head
     [:title "mdr2"]
     (include-css "/css/bootstrap.min.css")]
    [:body
     [:div.container
      (navbar user)
      body]]))

