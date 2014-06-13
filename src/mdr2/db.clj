(ns mdr2.db
  (:require [clojure.java.jdbc :as sql]))

(def db {:subprotocol "sqlite"
         :subname "db/mdr2.db"})

(defn get-production [id] 
  (let [results (sql/with-connection db 
                  (sql/with-query-results rs 
                    ["SELECT * FROM productions WHERE id = ?" id] 
                    (doall rs)))]
    (first results)))

(defn get-productions [] 
  (sql/with-connection db 
    (sql/with-query-results rs ["SELECT * FROM productions"] (doall rs))))

