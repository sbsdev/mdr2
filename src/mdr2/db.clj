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

(defn find-by-productnumber
  "Return production for given productnumber"
  [productnumber]
  (first (jdbc/query db ["SELECT * FROM production WHERE productNumber = ?" productnumber])))

(defn find-all
  "Return all productions"
  []
  (jdbc/query db ["SELECT * FROM production"]))

(defn add
  "Add the given `production`"
  [production]
  (jdbc/insert! db :production production))

(defn- update-or-insert!
  "Updates columns or inserts a new row in the specified table"
  [db table row where-clause]
  (jdbc/with-db-transaction [t-con db]
    (let [result (jdbc/update! t-con table row where-clause)]
      (if (zero? (first result))
        (jdbc/insert! t-con table row)
        result))))

(defn add-or-update!
  "Add or update the given `production`"
  [{libraryNumber :libraryNumber productNumber :productNumber :as production}]
  (cond
   libraryNumber (update-or-insert!
                  db :production production ["libraryNumber = ?" libraryNumber])
   productNumber (update-or-insert!
                  db :production production ["productNumber = ?" productNumber])
   :else (jdbc/insert! db :production production)))

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
