(ns mdr2.db
  "Persistence for productions"
  (:refer-clojure :exclude [find])
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as s]
            [environ.core :refer [env]]))

(def ^:private db (env :database-url))

(defn find
  "Return production for given `id`"
  [id]
  (first (jdbc/query db ["SELECT * FROM production WHERE id = ?" id])))

(defn find-all
  "Return all productions"
  []
  (jdbc/query db ["SELECT * FROM production"]))

(defn add
  "Add the given `production`"
  [production]
  (jdbc/insert! db :production production))

(defn delete
  "Remove the production with the given `id`"
  [id]
  (jdbc/delete! db :production ["id = ?" id]))

(defn get-user
  "Return the user with the given `username`"
  [username]
  (when-let [user (first (jdbc/query db ["SELECT * FROM user WHERE username = ?" username]))]
    (let [roles (jdbc/query db ["SELECT role.name from role JOIN user_role on user_role.role_id = role.id WHERE user_role.user_id = ?" (:id user)] 
                            :row-fn (comp keyword s/lower-case :name))]
      (assoc user :roles (set roles)))))

