(ns mdr2.test.archive
  (:use clojure.test)
  (:require [mdr2.archive]
            [clojure.java.io :refer [file]]
            [environ.core :refer [env]]))

(def mock-db (atom ()))

(defn mock-jdbc-insert [db table job]
  (reset! mock-db job))

(deftest test-archiving
  (testing "container id"
    (is (= (#'mdr2.archive/container-id {:id 123} :master) "dam123"))
    (is (= (#'mdr2.archive/container-id {:id 456} :master) "dam456")))

  (testing "container path"
    (is (= (#'mdr2.archive/container-path {:id 123} :master)
           (.getPath (file (env :archive-spool-dir) "dam123")))))

  (testing "rdf path"
    (is (= (#'mdr2.archive/container-rdf-path {:id 123} :master)
           (.getPath (file (env :archive-spool-dir) "dam123" "dam123.rdf")))))

  (testing "add master to db"
    (with-redefs [clojure.java.jdbc/insert! mock-jdbc-insert]
      (is (= (do
               (#'mdr2.archive/add-to-db {:id 123 :volumes 1} :master)
               @mock-db)
             {:verzeichnis "dam123",
              :sektion "master",
              :aktion "save",
              :transaktions_status "pending",
              :abholer "NN",
              :archivar "NN",
              :flags "x"}))))

  (testing "add iso to db"
    (with-redefs [clojure.java.jdbc/insert! mock-jdbc-insert]
      (is (= (do
               (#'mdr2.archive/add-to-db
                {:id 123 :volumes 1 :library_signature 7}
                :dist-master)
               @mock-db)
             {:verzeichnis "ds7",
              :sektion "cdimage",
              :aktion "save",
              :transaktions_status "pending",
              :abholer "NN",
              :archivar "NN",
              :flags "x"})))))
