(ns mdr2.db
  "Persistence for productions"
  (:refer-clojure :exclude [find])
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.string :as s]
            [environ.core :refer [env]]
            [mdr2.state :as state]))

(def ^:private db (env :database-url))

(defn map-state-to-kw [row]
  (update-in row [:state] state/from-int))

(defn map-state-to-int [row]
  (if-let [state (:state row)]
    (assoc row :state (states/to-int state))
    row))

(defn find
  "Return production for given `id`"
  [id]
  (first (jdbc/query db ["SELECT * FROM production WHERE id = ?" id]
                     :row-fn map-state-to-kw)))

(defn find-all
  "Return all productions"
  []
  (jdbc/query db ["SELECT production.* FROM production"]
              :row-fn map-state-to-kw))

(defn find-by-productnumber
  "Return the first production for given `product_number`"
  [product_number]
  (first (jdbc/query db ["SELECT * FROM production WHERE product_number = ?" product_number]
                     :row-fn map-state-to-kw)))

(defn find-by-state
  "Return all productions for given state"
  [s]
  (jdbc/query db ["SELECT * FROM production WHERE state = ?" (state/to-int s)]
              :row-fn map-state-to-kw))

(defn get-generated-key
  "Get the generated key from an `insert!` result. Returns `nil` if the
  result is not from an insert"
  [result]
  (let [m (first result)]
    (when (map? m) ; an insert returns a sequence of maps
      (-> m vals first))))

(defn add
  "Add the given `production`"
  [production]
  (if-let [key (get-generated-key (jdbc/insert! db :production (map-state-to-int production)))]
    (assoc production :id key)
    production))

(defn update!
  "Update the production with the given `id`, `product_number` or `library_number`"
  [{library_number :library_number product_number :product_number id :id :as production}]
  (when (or id product_number library_number)
    (jdbc/update! db :production (map-state-to-int production)
                  (cond id ["id = ?" id]
                        library_number ["library_number = ?" library_number]
                        product_number ["product_number = ?" product_number]))))

(defn- update-or-insert!
  "Updates columns or inserts a new row in the specified table"
  [db table row where-clause]
  (jdbc/with-db-transaction [t-con db]
    (let [result (jdbc/update! t-con table row where-clause)]
      (if (zero? (first result))
        (jdbc/insert! t-con table row)
        result))))

(defn add-or-update!
  "Add or update the given `production`. Return it possibly updated
  with an `:id` in the case of an insert"
  [{library_number :library_number product_number :product_number :as production}]
  (if-let [key (get-generated-key
                (if (or product_number library_number)
                  (update-or-insert! db :production (map-state-to-int production)
                   (cond library_number ["library_number = ?" library_number]
                         product_number ["product_number = ?" product_number]))
                  (jdbc/insert! db :production (map-state-to-int production))))]
    (assoc production :id key)
    production))

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

