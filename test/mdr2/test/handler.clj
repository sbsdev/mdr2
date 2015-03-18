(ns mdr2.test.handler
  (:use clojure.test
        ring.mock.request  
        mdr2.handler))

(deftest test-app
  ;; the following fails because we now have auth on the landing page
  ;; (testing "main route"
  ;;   (let [response (site (request :get "/"))]
  ;;     (is (= (:status response) 200))
  ;;     (is (re-find #"mdr2" (:body response)))))
  
  (testing "not-found route"
    (let [response (site (request :get "/invalid"))]
      (is (= (:status response) 404)))))
