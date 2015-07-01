(ns mdr2.test.util
  (:use clojure.test)
  (:require [clojure.java.io :refer [file]]
            [environ.core :refer [env]]
            [org.tobereplaced.nio.file :as nio]
            [mdr2.util :as util])
  (:import java.nio.file.FileAlreadyExistsException))

(defn create-test-dir []
  (let [root (.toFile (nio/create-temp-directory! "test-src"))]
    (nio/create-directory! (file root "a"))
    (nio/create-directory! (file root "b"))
    (spit (file root "1") "1")
    (spit (file root "a" "2") "1")
    (spit (file root "b" "3") "1")
    root))

(deftest copy-directory
  (testing "copy directory"
    (let [source (create-test-dir)
          dest-root (.toFile (nio/create-temp-directory! "test-dest"))
          dest (file dest-root "copy")
          _ (util/copy-directory! source dest)]
      (are [f] (nio/exists? f)
        (file dest "1")
        (file dest "a")
        (file dest "b")
        (file dest "a" "2")
        (file dest "b" "3"))
      (are [f] (= (slurp f) "1")
        (file dest "a" "2")
        (file dest "b" "3"))
      (util/delete-directory! source)
      (util/delete-directory! dest-root)))
  (testing "copy into existing directory"
    (let [source (create-test-dir)
          dest (nio/create-temp-directory! "test-dest")]
      (is (thrown? FileAlreadyExistsException (util/copy-directory! source dest)))
      (util/delete-directory! source)
      (util/delete-directory! dest))))
