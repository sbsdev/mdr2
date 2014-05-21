(ns mdr2.layout
  (:require [hiccup.page :refer [html5 include-css]]))

(defn common [& body]
  (html5
    [:head
     [:title "mdr2"]
     (include-css "/css/bootstrap.min.css")]
    [:body body]))
