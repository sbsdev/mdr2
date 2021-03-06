(ns mdr2.test.archive
  (:use clojure.test)
  (:require [clojure.java.io :refer [file]]
            [environ.core :refer [env]]
            [mdr2.archive :as archive]
            [mdr2.production :as prod]
            [org.tobereplaced.nio.file :as nio]
            [mdr2.util :as util]))

(def mock-db (atom []))

(def default-container-entry
  {:aktion "save",
   :transaktions_status "pending",
   :abholer "",
   :archivar "Madras2"
   :bemerkung ""
   :container_status "ok"})

(defn mock-jdbc-insert [db table job]
  (swap! mock-db conj (dissoc job :datum)))

(defn mock-set-state-archived! [production])

(defn setup-spool-dirs [test-fn]
  (nio/create-directories! (env :archive-spool-dir))
  (nio/create-directories! (env :archive-periodical-spool-dir))
  (nio/create-directories! (env :archive-other-spool-dir))
  (reset! mock-db [])
  (with-redefs [clojure.java.jdbc/insert! mock-jdbc-insert
                mdr2.production/set-state-archived! mock-set-state-archived!]
    (test-fn))
  (util/delete-directory! (env :archive-spool-dir))
  (util/delete-directory! (env :archive-periodical-spool-dir))
  (util/delete-directory! (env :archive-other-spool-dir)))

(use-fixtures :each setup-spool-dirs)

(deftest test-archiving
  (testing "container id"
    (is (= (#'mdr2.archive/container-id {:id 123} :master) "dam123"))
    (is (= (#'mdr2.archive/container-id {:id 456} :master) "dam456")))

  (testing "container path"
    (is (= (#'mdr2.archive/container-path {:id 123} :master)
           (file (env :archive-spool-dir) "dam123" "produkt"))))

  (testing "rdf path"
    (is (= (#'mdr2.archive/container-rdf-path {:id 123} :master)
           (.getPath (file (env :archive-spool-dir) "dam123" "dam123.rdf"))))))

(deftest add-master-to-db
  (testing "add master to db"
    (is (= (do
             (#'mdr2.archive/add-to-db {:id 123 :volumes 1 :revision 1} :master)
             @mock-db)
           [(merge {:verzeichnis "dam123"
                    :sektion "master"}
                   default-container-entry)]))))

(deftest add-iso-to-db
  (testing "add iso to db"
    (is (= (do
             (#'mdr2.archive/add-to-db
              {:id 123 :volumes 1 :library_signature 7 :revision 1}
              :dist-master)
             @mock-db)
           [(merge {:verzeichnis "7"
                    :sektion "cdimage"}
                   default-container-entry)]))))

(deftest book-single
  (testing "archiving a book with a single volume"
    (let [production {:id 14 :production_type "book" :volumes 1
                      :library_signature 15 :revision 1}
          dam-number (str "dam" (:id production))
          ds-number (str (:library_signature production))
          iso-name (str ds-number ".iso")
          spool-dir (env :archive-spool-dir)]
      (archive/archive production)
      ;; check the database entry
      (is (= @mock-db [(merge {:verzeichnis dam-number
                               :sektion "master"}
                              default-container-entry)
                       (merge {:verzeichnis ds-number
                               :sektion "cdimage"}
                              default-container-entry)]))
      (are [f] (nio/exists? f)
           ;; check if the master has been copied
           (file spool-dir dam-number "produkt" dam-number "aud001.wav")
           (file spool-dir dam-number "produkt" dam-number "ncc.html")
           (file spool-dir dam-number "produkt" dam-number "master.smil")
           ;; check if the master has an rdf
           (file spool-dir dam-number (str dam-number ".rdf"))
           ;; check if the distmaster has been copied...
           (file spool-dir ds-number "produkt" iso-name)
           ;; ...and that it has an rdf
           (file spool-dir ds-number (str ds-number ".rdf"))))))

(deftest book-multiple
  (testing "archiving a book with multiple volumes"
    (let [production {:id 14 :production_type "book" :volumes 2
                      :library_signature 15 :revision 1}
          dam-number (str "dam" (:id production))
          ds-number (str (:library_signature production))
          spool-dir (env :archive-spool-dir)]
      (archive/archive production)
      ;; check the database entry
      (is (= @mock-db [(merge {:sektion "master"
                               :verzeichnis dam-number}
                              default-container-entry)
                       (merge {:verzeichnis ds-number
                               :sektion "cdimage"}
                              default-container-entry)]))
      (are [f] (nio/exists? f)
           ;; check if the master has been copied
           (file spool-dir dam-number "produkt" dam-number "aud001.wav")
           (file spool-dir dam-number "produkt" dam-number "ncc.html")
           (file spool-dir dam-number "produkt" dam-number "master.smil")
           ;; check if the master has an rdf
           (file spool-dir dam-number (str dam-number ".rdf"))
           ;; check if all the distmasters have been copied...
           (file spool-dir ds-number "produkt" (str ds-number "_1.iso"))
           (file spool-dir ds-number "produkt" (str ds-number "_2.iso"))
           ;; ...and that it has an rdf
           (file spool-dir ds-number (str ds-number ".rdf"))))))

(deftest periodicals-single
  (testing "archiving of periodical single volume"
    (let [production {:id 14 :production_type "periodical"
                      :volumes 1 :revision 1}
          dam-number (str "dam" (:id production))
          rdf-name (str dam-number ".rdf")
          iso-name (str dam-number ".iso")
          spool-dir (env :archive-periodical-spool-dir)]
      (archive/archive production)
      (are [f] (nio/exists? f)
           (file spool-dir dam-number "produkt" iso-name)
           (file spool-dir dam-number rdf-name)))))

(deftest periodicals-multiple
  (testing "archiving of periodical multi volume"
    (let [production {:id 14 :production_type "periodical" :volumes 2
                      :revision 1}
          dam-number (prod/dam-number production)
          iso-name (str dam-number "_1.iso")
          rdf-name (str dam-number ".rdf")
          spool-dir (env :archive-periodical-spool-dir)]
      (archive/archive production)
      (are [f] (nio/exists? f)
           (file spool-dir dam-number "produkt" iso-name)
           (file spool-dir dam-number rdf-name)))))

(deftest other-single
  (testing "archiving of other production single volume"
    (let [product_number "DY1234"
          production {:id 14 :production_type "other" :volumes 1
                      :product_number product_number
                      :revision 1}
          dam-number (prod/dam-number production)
          iso-name (str dam-number ".iso")
          rdf-name (str dam-number ".rdf")
          spool-dir (env :archive-other-spool-dir)]
      (archive/archive production)
      (are [f] (nio/exists? f)
           (file spool-dir dam-number "produkt" iso-name)
           (file spool-dir dam-number rdf-name)))))

(deftest other-multiple
  (testing "archiving of other production multi volume"
    (let [product_number "DY1234"
          production {:id 14 :production_type "other"
                      :volumes 2 :product_number product_number
                      :revision 1}
          spool-dir (env :archive-other-spool-dir)
          dam-number (prod/dam-number production)
          rdf-name (str dam-number ".rdf")]
      (archive/archive production)
      (are [f] (nio/exists? f)
           (file spool-dir dam-number "produkt" (str dam-number "_1.iso"))
           (file spool-dir dam-number "produkt" (str dam-number "_2.iso"))
           (file spool-dir dam-number rdf-name)))))
