(ns mdr2.archive.core
  "Main entry point into the archive

For archiving we need to interface with an existing legacy system
named agadir. It doesn't do very much in very complicated ways.
Probably best to replace it at some point. In the mean time we try to
stay away from it and not to change too much. From reading the source
it appears that in order to archive a production you need to first
archive what they call the *master*, i.e. the dtb containing the wav
files and after that you'll have to archive the so-called *distribution
master* which is basically the same thing but the audio is encoded as
mp3 and the whole thing is packed up in one or more iso files

### Archiving the *master*

1. place it in a magic spool directory
2. generate an rdf file containing some meta data about the production
3. add an entry to a table in a database. Specify the `sektion` to be `master`

### Archiving the *distribution master*

1. Encode the audio to mp3
2. Pack everything up in an iso
3. place this iso in the magic spool directory
4. generate the rdf as above
5. add an entry to a table in a database. The `sektion` should be `cdimage`
"
  ;; FIXME: Most likely this should be split off into a separate lib that
  ;; replaces all of agadir at a later point in time. For instructions on
  ;; how to do this see
  ;; https://github.com/technomancy/leiningen/blob/stable/doc/DEPLOY.md

  (:require [clojure.java.io :refer [file]]
            [clojure.tools.logging :as log]
            [conman.core :as conman]
            [mdr2.config :refer [env]]
            [mdr2.db.core :as db]
            [java-time :as time]
            [babashka.fs :as fs]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.repair.core :as repair]
            [mdr2.rdf :as rdf]
            [iapetos.collector.fn :as prometheus]
            [mdr2.metrics :as metrics]))

;;(def ^:private db {:factory factory :name "java:jboss/datasources/archive"})
(def ^:private db {:name "java:jboss/datasources/archive"})

(defn- container-id
  "Return the name of a archive spool directory for a given
  `production` and `sektion`"
  ([production sektion]
   (container-id production sektion nil))
  ([production sektion volume]
   (case sektion
     :master (prod/dam-number production)
     :dist-master (str (:library_signature production)
                       (when (and volume
                                  (prod/multi-volume? production))
                         (str "_" volume))))))

(defn container-root-path
  "Return the root container path for a given `production` and
  `sektion`"
  [production sektion]
  (file (env :archive-spool-dir) (container-id production sektion)))

(defn container-path
  "Return the path to the archive spool directory for a given
  `production` and `sektion`"
  [production sektion]
  (let [root-path (container-root-path production sektion)]
    (file root-path "produkt")))

(defn container-rdf-path
  "Return the path to the rdf file in the archive spool for a given
  `production` and `sektion`"
  [production sektion]
  (let [root-path (container-root-path production sektion)
        rdf-name (str (container-id production sektion) ".rdf")]
    (.getPath (file root-path rdf-name))))

(defn- db-job
  "Return a map that can be used to insert a job in the archive db for
  given `production` and `sektion`"
  [production sektion]
  {:archivar "Madras2"
   :abholer ""
   :aktion (if (> (:revision production) 1) "update" "save")
   :transaktions_status "pending"
   :container_status "ok"
   :bemerkung ""
   :verzeichnis (container-id production sektion)
   :sektion (case sektion :master "master" :dist-master "cdimage")
   :datum (time/local-date)})

(defn- add-to-db
  "Insert a `production` into the archive db for the given `sektion`.
  This marks the files in the spool directory as ready for archiving
  and concludes the archiving process from the point of view of the
  production system."
  [production sektion]
  (let [update (> (:revision production) 1)
        job (db-job production sektion)]
    (if update
      (let [container-id (repair/container-id production sektion)]
        (log/debugf "Updating %s (%s, %s) in archive db" (:id production) sektion container-id)
        (db/update-archive-container-job (assoc job :container-id container-id)))
      (do
        (log/debugf "Adding %s (%s) to archive db" (:id production) sektion)
        (db/insert-archive-container-job job)))))

(defn set-file-permissions
  "Set file permissions on `file-tree` to g+w recursively"
  [file-tree]
  (let [visitor-fn (fn [f] (fs/set-posix-file-permissions f "rw-rw-r--") nil)]
    (fs/walk-file-tree file-tree {:pre-visit-dir visitor-fn :visit-file visitor-fn})))

(defn- copy-files
  "Copy a `production` to the archive spool dir for the given
  `sektion`. For a production master copy the whole DTB including wav
  files. For a production distribution master copy the isos"
  [production sektion]
  (let [archive-root-path (container-root-path production sektion)]
    (if (fs/exists? archive-root-path)
      (let [message (format "Archive root path %s already exists" archive-root-path)]
        (log/error message)
        (throw (ex-info message {:error-id ::directory-already-exists})))
      (let [archive-path (container-path production sektion)]
        (fs/create-dirs archive-path)
        (log/debugf "Copying files for %s (%s)" (:id production) sektion)
        (case sektion
          :master
          (fs/copy-tree (path/recorded-path production)
                        (file archive-path (prod/dam-number production)))
          :dist-master
          (doseq [volume (range 1 (inc (:volumes production)))]
            (let [iso-archive-name (str (container-id production sektion volume) ".iso")
                  iso-archive-path (file archive-path iso-archive-name)]
              (fs/copy (path/iso-name production volume) iso-archive-path))))
        (set-file-permissions archive-root-path)))))

(defn- create-rdf
  "Create an rdf file and place it in the appropriate archive spool
  directory"
  [production sektion]
  (let [rdf (rdf/rdf production)
        rdf-path (container-rdf-path production sektion)]
    (log/debugf "Creating rdf for %s (%s)" (:id production) sektion)
    (spit rdf-path rdf)))

(defn- archive-sektion
  "Archive a `production` for given `sektion`. For the :master sektion
  copy the original DTB including the wav files. For the :dist-master
  sektion copy one or more iso files"
  [production sektion]
  ;; place all the files in the spool dir
  (copy-files production sektion)
  ;; create an rdf file
  (create-rdf production sektion)
  ;; add it to the db so that the agadir machinery will pick it up
  (add-to-db production sektion))

(defmulti archive
  "Archive a `production`

  There are multiple ways to archive an production.

  1. for a *book* we simply archive both the master and the dist-master
  2. for a *periodical* we archive the master and then put the
     dist-master into a special spool directory based on the setting
     `:archive-periodical-spool-dir`
  3. for *other* productions we archive the master and then put the
     dist-master into a special spool directory based on the setting
     `:archive-other-spool-dir`"
  (fn [production] (:production_type production))
  :default "book")

(defmethod archive "book"
  [production]
  (conman/with-transaction [db/*archive-db*]
    (archive-sektion production :master)
    (archive-sektion production :dist-master)
    (prod/set-state-archived! production)))

(defmethod archive "periodical"
  [production]
  (conman/with-transaction [db/*archive-db*]
    (archive-sektion production :master)
    ;; archive the periodical iso(s)
    (let [dam-number (prod/dam-number production)
          archive-path (.getPath (file (env :archive-periodical-spool-dir) dam-number))
          multi-volume? (prod/multi-volume? production)]
      (when (fs/exists? archive-path)
        ;; when repairing the production is already in the spool dir
        (when-not (fs/delete-tree archive-path)
          (let [message (format "Failed to remove archive path for periodical (%s)"
                                archive-path)]
            (log/error message)
            (throw (ex-info message {:error-id ::spool-dir-remove-failed})))))
      (fs/create-dirs archive-path)
      ;; create the rdf
      (let [rdf-path (file archive-path (str dam-number ".rdf"))
            rdf (rdf/rdf production)]
        (spit rdf-path rdf))
      ;; copy all volumes
      (doseq [volume (range 1 (inc (:volumes production)))]
        (let [iso-archive-name (str dam-number (when multi-volume? (str "_" volume)) ".iso")
              iso-archive-path (file archive-path "produkt" iso-archive-name)]
          (fs/create-dirs (fs/parent iso-archive-path))
          (fs/copy (path/iso-name production volume) iso-archive-path)))
      (set-file-permissions (file archive-path))
      (prod/set-state-archived! production))))

(defmethod archive "other"
  [production]
  (conman/with-transaction [db/*archive-db*]
    (archive-sektion production :master)
    ;; place the iso(s) in a spool directory
    (let [dam-number (prod/dam-number production)
          archive-path (.getPath (file (env :archive-other-spool-dir) dam-number))
          multi-volume? (prod/multi-volume? production)]
      (when (fs/exists? archive-path)
        ;; when repairing the production is already in the spool dir
        (when-not (fs/delete-tree archive-path)
          (let [message (format "Failed to remove archive path for other production (%s)"
                                archive-path)]
            (log/error message)
            (throw (ex-info message {:error-id ::spool-dir-remove-failed})))))
      (fs/create-dirs archive-path)
      ;; create the rdf
      (let [rdf-path (file archive-path (str dam-number ".rdf"))
            rdf (rdf/rdf production)]
        (spit rdf-path rdf))
      ;; copy all volumes
      (doseq [volume (range 1 (inc (:volumes production)))]
        (let [iso-archive-name (str dam-number (when multi-volume? (str "_" volume)) ".iso")
              iso-archive-path (file archive-path "produkt" iso-archive-name)]
          (fs/create-dirs (fs/parent iso-archive-path))
          (fs/copy (path/iso-name production volume) iso-archive-path)
          (set-file-permissions (file iso-archive-path))))
      (prod/set-state-archived! production))))

(prometheus/instrument! metrics/registry #'archive)

(comment
  (let [p {:id 50000 :revision 0 :library_signature "ds70000"}]
    (db-job p :master))

  (let [p {:id 50000 :revision 0 :library_signature "ds70000"}]
    (add-to-db p :master))

  (let [p {:id 50000 :revision 2 :library_signature "ds70000"}]
    (add-to-db p :master))

  )
