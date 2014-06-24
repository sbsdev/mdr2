(ns mdr2.db
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as s]))

(def db {:subprotocol "sqlite"
         :subname "db/mdr2.db"})

(defn get-production [id]
  (first (jdbc/query db ["SELECT * FROM production WHERE id = ?" id])))

(defn get-productions []
  (jdbc/query db ["SELECT * FROM production"]))

(defn add-production [production]
    (jdbc/insert! db :productions production))

(defn get-user [username]
  (when-let [user (first (jdbc/query db ["SELECT * FROM user WHERE username = ?" username]))]
    (let [roles (jdbc/query db ["SELECT role.name from role JOIN user_role on user_role.role_id = role.id WHERE user_role.user_id = ?" (:id user)] 
                            :row-fn (comp keyword s/lower-case :name))]
      (assoc user :roles (set roles)))))

