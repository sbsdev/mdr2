(ns mdr2.util
  "Common utilies"
  (:require [org.tobereplaced.nio.file :as nio]))

(defn delete-directory!
  "Delete `directory` recursively. See
  https://github.com/ToBeReplaced/nio.file/blob/master/walkthrough.clj"
  [directory]
  (nio/walk-file-tree directory
   (nio/naive-visitor
    :post-visit-directory nio/delete!
    :visit-file nio/delete!)))

