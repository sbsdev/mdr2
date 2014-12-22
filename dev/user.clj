(ns user
  (:require [robert.hooke :as rh]
            [clojure.tools.logging :as log]))

;; inspiration from
;; http://martintrojer.github.io/clojure/2014/03/09/working-with-coreasync-blocking-calls/

(defn debug [{:keys [name ns]} f & args]
  (log/debug "Calling " (ns-name ns) "/" name " with " args)
  (apply f args))

(defn enable-debug [var]
  (log/debug "Enabling debugging for" var)
  (robert.hooke/add-hook var (partial debug (meta var))))

(defn disable-debug [var]
  (log/debug "Disabling debugging for" var)
  (robert.hooke/clear-hooks var))

(defn public-fns [sym]
  (->> sym
   find-ns
   ns-publics
   vals
   (filter #(fn? (deref %)))))

(defn debug-ns [sym]
  (->> (public-fns sym)
   ;; remove dups
   (remove #(:robert.hooke/hooks (meta (deref %)))) ; this doesn't seem to work
   (map enable-debug)
   doall))

(defn undebug-ns [sym]
  (->> (public-fns sym)
   ;; (filter #(:robert.hooke/hook (meta (deref %))))
   (map disable-debug)
   doall))

(comment
  (debug-ns 'mdr2.db))
