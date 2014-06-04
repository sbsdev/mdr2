(ns mdr2.db
  (:require [clojure.java.jdbc :as sql]))

(def db {:subprotocol "sqlite"
         :subname "db/database.db"})

(defn create-productions-table []
  (try (sql/with-connection db
         (sql/create-table :productions
                           [:date :text]
                           [:name :text]
                           [:status :text]))
       (catch Exception e (println e))))

(def testdata
  {:date "2011-9-12",
   :name "haha",
   :status "Done"
   })

(create-db)

(sql/with-connection db
  (sql/insert-records :productions testdata))

(defn get-productions [] 
  (sql/with-connection db 
    (sql/with-query-results rs ["select * from productions"] (doall rs))))
