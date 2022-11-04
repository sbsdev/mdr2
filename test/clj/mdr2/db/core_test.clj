(ns mdr2.db.core-test
  (:require
   [mdr2.db.core :refer [*db*] :as db]
   [java-time.pre-java8]
   [luminus-migrations.core :as migrations]
   [clojure.test :refer :all]
   [next.jdbc :as jdbc]
   [mdr2.config :refer [env]]
   [mount.core :as mount]))

(use-fixtures
  :once
  (fn [f]
    (mount/start
     #'mdr2.config/env
     #'mdr2.db.core/*db*)
    (migrations/migrate ["migrate"] (select-keys env [:database-url]))
    (f)))

(comment
  (deftest test-production
    (jdbc/with-transaction [t-conn *db* {:rollback-only true}]
      (is (= 1 (db/insert-production
                t-conn
                {:title "The War of the Worlds"
                 :date 1898
                 :identifier "b0c3f96e-5c32-11ed-9b6a-0242ac120002"
                 :state "new"})))
      (is (= {}
             (db/get-production t-conn {:id "1"}))))))
