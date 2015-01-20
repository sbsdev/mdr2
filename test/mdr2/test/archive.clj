(ns mdr2.test.archive
  (:use clojure.test)
  (:require [mdr2.archive :as archive]
            [me.raynes.fs :as fs]
            [clojure.java.io :refer [file]]
            [environ.core :refer [env]]))

(def mock-db (atom ()))

(defn mock-jdbc-insert [db table job]
  (reset! mock-db job))

(defn setup-spool-dirs [test-fn]
  (fs/mkdirs (env :archive-spool-dir))
  (fs/mkdirs (env :archive-periodical-spool-dir))
  (fs/mkdirs (env :archive-other-spool-dir))
  (test-fn)
  (fs/delete-dir (env :archive-spool-dir))
  (fs/delete-dir (env :archive-periodical-spool-dir))
  (fs/delete-dir (env :archive-other-spool-dir)))

(use-fixtures :each setup-spool-dirs)

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

(deftest periodicals-single
  (testing "archiving of periodical single volume"
    (let [production {:id 14 :production_type "periodical" :volumes 1}
          dam-number (str "dam" (:id production))
          rdf-name (str dam-number ".rdf")
          iso-name (str dam-number ".iso")
          spool-dir (env :archive-periodical-spool-dir)]
      (archive/archive production)
      (is (fs/exists? (fs/file spool-dir dam-number "produkt" iso-name)))
      (is (fs/exists? (fs/file spool-dir dam-number rdf-name))))))

(deftest periodicals-multiple
  (testing "archiving of periodical multi volume"
    (let [production {:id 14 :production_type "periodical" :volumes 2}
          dam-number (str "dam" (:id production))
          iso-name (str dam-number "_1.iso")
          rdf-name (str dam-number ".rdf")
          spool-dir (env :archive-periodical-spool-dir)]
      (archive/archive production)
      (is (fs/exists? (fs/file spool-dir dam-number "produkt" iso-name)))
      (is (fs/exists? (fs/file spool-dir dam-number rdf-name))))))

(deftest other-single
  (testing "archiving of other production single volume"
    (let [product_number "DY1234"
          production {:id 14 :production_type "other" :volumes 1 :product_number product_number}
          iso-name (str product_number ".iso")
          spool-dir (env :archive-other-spool-dir)]
      (archive/archive production)
      (is (fs/exists? (fs/file spool-dir iso-name)))
      (is (not (fs/exists? (fs/file spool-dir (str product_number ".rdf"))))))))

(deftest other-multiple
  (testing "archiving of other production multi volume"
    (let [product_number "DY1234"
          production {:id 14 :production_type "other" :volumes 2 :product_number product_number}
          iso-name (str product_number "_1.iso")
          spool-dir (env :archive-other-spool-dir)]
      (archive/archive production)
      (is (fs/exists? (fs/file spool-dir iso-name)))
      (is (not (fs/exists? (fs/file spool-dir (str product_number ".rdf"))))))))
