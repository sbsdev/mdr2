(ns mdr2.db.core
  (:require
   [next.jdbc.date-time]
   [next.jdbc.result-set]
   [clojure.string :as string]
   [clojure.tools.logging :as log]
   [conman.core :as conman]
   [mdr2.config :refer [env]]
   [mount.core :refer [defstate]]))

(defstate ^:dynamic *db*
  :start (if-let [jdbc-url (env :database-url)]
           (conman/connect! {:jdbc-url jdbc-url})
           (do
             (log/warn "database connection URL was not found, please set :database-url in your config, e.g: dev-config.edn")
             *db*))
  :stop (conman/disconnect! *db*))

(conman/bind-connection *db* "sql/queries.sql")

(defstate ^:dynamic *archive-db*
  :start (if-let [jdbc-url (env :archive-database-url)]
           (conman/connect! {:jdbc-url jdbc-url})
           (do
             (log/warn "database connection URL was not found, please set :archive-database-url in your config, e.g: dev-config.edn")
             *archive-db*))
  :stop (conman/disconnect! *archive-db*))

(conman/bind-connection *archive-db* "sql/archive-queries.sql")

(defn search-to-sql
  "Prepare given search string `s` for search in SQL. If the string
  neither starts with '^' nor ends with '$' then it is simply wrapped
  in '%'. If it starts with '^' or ends with '$' then the respective
  '%' is not added."
  [s]
  (let [prepend #(str %2 %1)
        append #(str %1 %2)]
    (cond-> s
      (not (string/starts-with? s "^")) (prepend "%")
      (not (string/ends-with? s "$")) (append "%")
      true (string/replace #"[$^]" ""))))

(extend-protocol next.jdbc.result-set/ReadableColumn
  java.sql.Timestamp
  (read-column-by-label [^java.sql.Timestamp v _]
    (.toLocalDateTime v))
  (read-column-by-index [^java.sql.Timestamp v _2 _3]
    (.toLocalDateTime v))
  java.sql.Date
  (read-column-by-label [^java.sql.Date v _]
    (.toLocalDate v))
  (read-column-by-index [^java.sql.Date v _2 _3]
    (.toLocalDate v))
  java.sql.Time
  (read-column-by-label [^java.sql.Time v _]
    (.toLocalTime v))
  (read-column-by-index [^java.sql.Time v _2 _3]
    (.toLocalTime v)))

(defn insert-production
  "Insert the given `production`. Return it with the updated primary key `id`"
  [production]
  (try
    (if-let [key (-> (insert-raw production) first :generated_key)]
      (assoc production :id key)
      production)
    (catch clojure.lang.ExceptionInfo e
      (condp = (type (ex-cause e))
        java.sql.SQLIntegrityConstraintViolationException (throw (ex-info (ex-message e) {:error-id :duplicate-key}))
        (throw e))))) ; any other exception is just passed on
