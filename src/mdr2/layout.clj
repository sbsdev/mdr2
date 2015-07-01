(ns mdr2.layout
  "Define the basic page structure and layout"
  (:require [clojure.string :as s]
            [clojure.data.json :as json]
            [hiccup.page :refer [html5 include-css include-js]]
            [cemerick.friend :as friend]))

(defn key-to-label
  "Return a nice string from a keyword, e.g. `:product_number` will be
  converted to \"Product Number\""
  [k]
  (->> (s/split (name k) #"_")
       (map s/capitalize)
       (s/join " ")))

(defn glyphicon
  ([class]
   (glyphicon class nil))
  ([class tooltip]
   [:span (into {} (list [:class (str "glyphicon glyphicon-" class)]
                         (when tooltip [:title tooltip])))]))

(defn button
  "Return a button. If the `href` is nil the button is disabled"
  [href & body]
  (if href
    [:a.btn.btn-default {:href href} body]
    [:button.btn.btn-default {:disabled "disabled"} body]))

(defn menu-item [href & body]
  [:li [:a {:href href} body]])

(defn dropdown [items & body]
  [:div.btn-group
   [:button.btn.btn-default.dropdown-toggle {:type "button" :data-toggle "dropdown"}
    body
    [:span.caret]]
   [:ul.dropdown-menu {:role "menu"}
    (for [item items] item)]])

(defn button-group [buttons]
  [:div.btn-group
   (for [button buttons] button)])

(defn loginbar 
  "Display a login link or information about the currently logged in user if user is non-nil"
  [identity]
  (let [user (friend/current-authentication identity)]
    [:ul.nav.navbar-nav.navbar-right
     (if user
       (list
        [:li [:a [:b (format "%s %s" (:first_name user) (:last_name user))]]]
        [:li [:a {:href "/logout"} (glyphicon "log-out")]])
       [:li [:a {:href "/login"} (glyphicon "log-in")]])]))

(defn dropdown-menu
  "Display a dropdown menu"
  [identity]
  (let [menu [{:href "/catalog" :label "Assign Library Signature" :roles #{:catalog :it}}
              ;; archived productions are visible for any role
              {:href "/production/archived" :label "Archived Productions" :roles nil}
              {:href "/production/upload" :label "Import from Vubis" :roles #{:admin :it}}
              {:href "/production/repair" :label "Repair Production" :roles #{:admin :studio :it}}]
        filtered (filter #(or
                           ;; if no role is defined we allow access
                           (nil? (:roles %))
                           ;; otherwise we check if the role is authorized
                           (friend/authorized? (:roles %) identity)) menu)]
    (when (seq filtered)
      [:li.dropdown
       [:a.dropdown-toggle
        {:href "#" :role "button" :data-toggle "dropdown" :aria-expanded false} "Actions" [:span.caret]]
       [:ul.dropdown-menu {:role "menu"}
        (for [{:keys [href label roles]} filtered]
          [:li [:a {:href href} label]])]])))

(defn navbar
  "Display the navbar"
  [identity]
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
     [:ul.nav.navbar-nav (dropdown-menu identity)]
     [:ul.nav.navbar-nav.navbar-right (loginbar identity)]]]])

(def ^:private datatable-config
  {:paging false
   :paginType "simple"
   :pageLength 50
   :lengthChange false
   :columnDefs [{:targets "orderable-false"
                 :searchable false
                 :orderable false}]})

(defn common
  "Display a page using the bootstrap css"
  [identity & body]
  (html5
    [:head
     [:title "mdr2"]
     (include-css "/css/bootstrap.min.css")
     (include-css "/css/dataTables.bootstrap.css")]
    [:body
     [:div.container
      (navbar identity)
      body]
     (include-js "/js/jquery-1.11.1.min.js")
     (include-js "/js/bootstrap.min.js")
     (include-js "/js/jquery.dataTables.min.js")
     (include-js "/js/dataTables.bootstrap.min.js")
     [:script
      (format
       "$(document).ready( function () { $('#%s').DataTable( %s ); } );"
       "productions"
       (json/write-str datatable-config))]]))

