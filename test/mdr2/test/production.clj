(ns mdr2.test.production
  (:use clojure.test)
  (:require [clojure.java.io :refer [file]]
            [mdr2.production :as production]
            [environ.core :refer [env]]))

(defn mock-jdbc-query [_ & _]
  [{:id 123}])

(deftest test-production
  (testing "find a production"
    (with-redefs [clojure.java.jdbc/query mock-jdbc-query]
      (is (= (production/find 123) {:id 123})))))
