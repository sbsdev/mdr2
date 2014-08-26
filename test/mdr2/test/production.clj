(ns mdr2.test.production
  (:use clojure.test)
  (:require [mdr2.production :as production]))

(defn mock-jdbc-query [_ _]
  [{:id 123}])

(deftest test-production
  (testing "find a production"
    (with-redefs [clojure.java.jdbc/query mock-jdbc-query]
      (is (= (production/find 123) {:id 123}))))

  (testing "get production path"
    (is (= (production/path "foo") "/var/lib/mdr2/foo"))))
