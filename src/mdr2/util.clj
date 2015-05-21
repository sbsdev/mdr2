(ns mdr2.util
  "Common utilies"
  (:require [org.tobereplaced.nio.file :as nio]
            [clojure.tools.logging :as log])
  (:import (java.nio.file FileSystemException
                          DirectoryNotEmptyException
                          FileVisitResult)
           java.lang.InterruptedException))

(def ^:private delete-retry-millis 100)

;; when deleting a directory that is on an nfs mount we sometimes get
;; a DirectoryNotEmptyException. This might have to do with some file
;; handles that haven't been garbage collected. So if we fail to
;; delete we try again a bit harder. For some inspiration see
;; https://svn.apache.org/viewvc/ant/core/trunk/src/main/org/apache/tools/ant/util/FileUtils.java?view=markup.
(defn try-hard-to-delete [directory]
  (log/warnf "Directory not empty %s. Trying harder" directory)
  (System/gc) ; do garbage collection
  ;; wait
  (try
    (Thread/sleep delete-retry-millis)
    (catch InterruptedException e))
  ;; and then try to delete again
  (log/warnf "Now trying again to delete %s" directory)
  (try
    (nio/delete! directory)
    (catch DirectoryNotEmptyException e
      (log/error e))))

(defn post-visit-directory [directory]
  ;; Deal with nfs issues.
  (try
    (nio/delete! directory)
    (catch DirectoryNotEmptyException e
      (try-hard-to-delete directory))))

(defn delete-directory!
  "Delete `directory` recursively. See
  https://github.com/ToBeReplaced/nio.file/blob/master/walkthrough.clj"
  [directory]
  (try
    (nio/walk-file-tree
     directory
     (nio/naive-visitor
      :post-visit-directory post-visit-directory
      :visit-file nio/delete!))
    (catch FileSystemException e
      (log/error e))))

(defn copy-directory!
  "Copy a directory from `from` to `to` recursively"
  [from to]
  (let [copy (fn [path]
               (nio/copy! path (nio/resolve-path to (nio/relativize from path)))
               FileVisitResult/CONTINUE)]
    (nio/walk-file-tree
     from
     (nio/naive-visitor
      :pre-visit-directory copy
      :visit-file copy))))
