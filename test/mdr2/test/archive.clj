(ns mdr2.test.archive
  (:use clojure.test
        mdr2.archive)
  (:require [clojure.java.io :refer [file]]
            [environ.core :refer [env]]))

(def mock-db (atom ()))

(defn mock-jdbc-insert [db table job]
  (reset! mock-db job))

(deftest test-archiving
  (testing "container id"
    (is (= (container-id {:id 123}) "dam123"))
    (is (= (container-id {:id 456}) "dam456")))

  (testing "container path"
    (is (= (container-path {:id 123}) (.getPath (file (env :archive-spool-dir) "dam123")))))

  (testing "rdf path"
    (is (= (container-rdf-path {:id 123})
           (.getPath (file (env :archive-spool-dir) "dam123" "dam123.rdf")))))

  (testing "add master to db"
    (with-redefs [clojure.java.jdbc/insert! mock-jdbc-insert]
      (is (= (do
               (add-to-db {:id 123})
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
               (add-to-db {:id 123 :iso-path "foo"})
               @mock-db)
             {:verzeichnis "ds123",
              :sektion "cdimage",
              :aktion "save",
              :transaktions_status "pending",
              :abholer "NN",
              :archivar "NN",
              :flags "x"})))))
