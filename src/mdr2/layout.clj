(ns mdr2.layout
  "Define the basic page structure and layout"
  (:require [hiccup.page :refer [html5 include-css]]))

(defn loginbar 
  "Display a login link or information about the currently logged in user if user is non-nil"
  [user]
  [:ul.nav.navbar-nav.navbar-right
   (if user
     (list
      [:li [:a "Logged in as " [:b (:username user)]]]
      [:li [:a {:href "/logout"} "Logout"]])
     [:li [:a {:href "/login"} "Login"]])])

(defn navbar
  "Display the navbar"
  [user]
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
      (loginbar user)]]]])

(defn common
  "Display a page using the bootstrap css"
  [user & body]
  (html5
    [:head
     [:title "mdr2"]
     (include-css "/css/bootstrap.min.css")]
    [:body
     [:div.container
      (navbar user)
      body]]))

