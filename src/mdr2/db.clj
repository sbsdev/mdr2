(ns mdr2.db
  (:require [clojure.java.jdbc :as jdbc]))

(def db {:subprotocol "sqlite"
         :subname "db/mdr2.db"})

(defn get-production [id]
  (first (jdbc/query db ["SELECT * FROM productions WHERE id = ?" id])))

(defn get-productions []
  (jdbc/query db ["SELECT * FROM productions"]))

(defn add-production [production]
    (jdbc/insert! db :productions production))
