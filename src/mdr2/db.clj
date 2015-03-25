(ns mdr2.db
  "Persistence for productions"
  (:refer-clojure :exclude [find])
  (:require [clojure.java.jdbc :as jdbc]
            [immutant.transactions :refer [set-rollback-only]]
            [immutant.transactions.jdbc :refer [factory]]
            [yesql.core :refer [defqueries]]
            [clojure.string :as s]
            [environ.core :refer [env]])
  (:import java.sql.SQLException))

(def ^:private db {:factory factory :name "java:jboss/datasources/productions"})

(defqueries "sql/queries.sql" {:connection db})

(defn get-generated-key
  "Get the generated key from an `insert!` result. Returns `nil` if the
  result is not from an insert"
  [result]
  (let [m (first result)]
    (when (map? m) ; an insert returns a sequence of maps
      (-> m vals first))))

(defn insert!
  "Insert the given `production`"
  [production]
  (try
    (if-let [key (get-generated-key (jdbc/insert! db :production production))]
      (assoc production :id key)
      production)
    (catch SQLException e
      (set-rollback-only) ; make sure transactions are rolled back
      [(.getMessage e)])))

(defn update!
  "Update the production with the given `id`, `product_number` or `library_number`"
  [{:keys [library_number product_number id] :as production}]
  (when (or id product_number library_number)
    (jdbc/update! db :production production
                  (cond id ["id = ?" id]
                        library_number ["library_number = ?" library_number]
                        product_number ["product_number = ?" product_number]))))

(defn get-user
  "Return the user with the given `username`"
  [username]
  (when-let [user (find-user {:id username}
                             {:result-set-fn first})]
    (let [roles (find-user-roles {:id (:id user)}
                                 {:row-fn (comp keyword s/lower-case :role_id)})]
      (assoc user :roles (set roles)))))

