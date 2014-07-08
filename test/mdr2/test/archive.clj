(ns mdr2.test.archive
  (:use clojure.test
        mdr2.archive))

(deftest test-archiving
  (testing "container id"
    (is (= (container-id {:id 123}) "dam123"))
    (is (= (container-id {:id 456}) "dam456")))

  (testing "container path"
    (is (= (.getPath (container-path {:id 123})) "/var/spool/agadir/dam123")))
  
  (testing "rdf path"
    (is (= (.getPath (container-rdf-path {:id 123})) "/var/spool/agadir/dam123/dam123.rdf"))))
