(ns mdr2.test.archive
  (:use clojure.test
        mdr2.archive))

(defn mock-jdbc-insert [db table job] job)

(deftest test-archiving
  (testing "container id"
    (is (= (container-id {:id 123}) "dam123"))
    (is (= (container-id {:id 456}) "dam456")))

  (testing "container path"
    (is (= (container-path {:id 123}) "/var/spool/agadir/dam123")))

  (testing "rdf path"
    (is (= (container-rdf-path {:id 123})
           "/var/spool/agadir/dam123/dam123.rdf")))

  (testing "add master to db"
    (with-redefs [clojure.java.jdbc/insert! mock-jdbc-insert]
      (is (= (add-to-db {:id 123})
             {:verzeichnis "dam123",
              :sektion "master",
              :aktion "save",
              :transaktions_status "pending",
              :abholer "NN",
              :archivar "NN",
              :flags "x"}))))

  (testing "add iso to db"
    (with-redefs [clojure.java.jdbc/insert! mock-jdbc-insert]
      (is (= (add-to-db {:id 123 :iso-path "foo"})
             {:verzeichnis "ds123",
              :sektion "master",
              :aktion "save",
              :transaktions_status "pending",
              :abholer "NN",
              :archivar "NN",
              :flags "x"})))))
