(ns mdr2.archive
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

  (:require [clojure.java.jdbc :as jdbc]
            [clojure.java.io :refer [file]]
            [clojure.tools.logging :as log]
            [immutant.transactions :refer [transaction]]
            [immutant.transactions.jdbc :refer [factory]]
            [environ.core :refer [env]]
            [clj-time.core :as t]
            [clj-time.coerce :refer [to-date]]
            [org.tobereplaced.nio.file :as nio]
            [me.raynes.fs :as fs]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.repair :as repair]
            [mdr2.rdf :as rdf]))

(def ^:private db {:factory factory :name "java:jboss/datasources/archive"})

(def spool-dir
  "Path to the archive spool directory, i.e. where to place incoming
  productions that are to be archived"
   (env :archive-spool-dir))

(def periodical-spool-dir
  "Path to the archive spool directory for periodicals"
  (env :archive-periodical-spool-dir))

(def other-spool-dir
  "Path to the archive spool directory for other productions"
  (env :archive-other-spool-dir))

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
  (file spool-dir (container-id production sektion)))

(defn container-path
  "Return the path to the archive spool directory for a given
  `production` and `sektion`"
  [production sektion]
  (let [root-path (container-root-path production sektion)]
    (.getPath (file root-path "produkt"))))

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
   :datum (to-date (t/now))})

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
        (jdbc/update! db :container job ["id = ?" container-id]))
      (jdbc/insert! db :container job))))

(defn set-file-permissions
  "Set file permissions on `root` to g+w recursively"
  [file-tree]
  (let [permissions (conj (set (nio/posix-file-permissions file-tree))
                          (nio/posix-file-permission :group-write))
        visitor-fn (fn [f] (nio/set-posix-file-permissions! f permissions) nil)
        visitor (nio/naive-visitor
                 :pre-visit-directory visitor-fn
                 :visit-file visitor-fn)]
    (nio/walk-file-tree file-tree visitor)))

(defn- copy-files
  "Copy a `production` to the archive spool dir for the given
  `sektion`. For a production master copy the whole DTB including wav
  files. For a production distribution master copy the isos"
  [production sektion]
  (let [archive-root-path (container-root-path production sektion)]
    (if (fs/exists? archive-root-path)
      (log/errorf "Archive root path %s already exists" archive-root-path)
      (let [archive-path (container-path production sektion)]
        (fs/mkdir archive-root-path)
        (case sektion
          :master
          (fs/copy-dir (path/recorded-path production)
                       (file archive-path (prod/dam-number production)))
          :dist-master
          (doseq [volume (range 1 (inc (:volumes production)))]
            (let [iso-archive-name (str (container-id production sektion volume) ".iso")
                  iso-archive-path (.getPath (file archive-path iso-archive-name))]
              (fs/copy+ (path/iso-name production volume) iso-archive-path))))
        (set-file-permissions archive-root-path)))))

(defn- create-rdf
  "Create an rdf file and place it in the appropriate archive spool
  directory"
  [production sektion]
  (let [rdf (rdf/rdf production)
        entries (case sektion :master 2 :dist-master (inc (:volumes production)))]
    (let [rdf-path (container-rdf-path production sektion)]
      (spit rdf-path rdf))))

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
  "Archive a `production`"
  (fn [production] (:production_type production))
  :default "book")

(defmethod archive "book"
  [production]
  (transaction
   (archive-sektion production :master)
   (archive-sektion production :dist-master)
   (prod/set-state-archived! production)))

(defmethod archive "periodical"
  [production]
  (transaction
   (archive-sektion production :master)
   ;; archive the periodical iso(s)
   (let [dam-number (prod/dam-number production)
         archive-path (.getPath (file periodical-spool-dir dam-number))
         multi-volume? (prod/multi-volume? production)]
     (if (fs/exists? archive-path)
       (log/errorf "Archive path %s for periodical already exists" archive-path)
       (do
         (fs/mkdir archive-path)
         ;; create the rdf
         (let [rdf-path (file archive-path (str dam-number ".rdf"))
               rdf (rdf/rdf production)]
           (spit rdf-path rdf))
         ;; copy all volumes
         (doseq [volume (range 1 (inc (:volumes production)))]
           (let [iso-archive-name (str dam-number (when multi-volume? (str "_" volume)) ".iso")
                 iso-archive-path (.getPath (file archive-path "produkt" iso-archive-name))]
             (fs/copy+ (path/iso-name production volume) iso-archive-path)))
         (set-file-permissions (file archive-path))
         (prod/set-state-archived! production))))))

(defmethod archive "other"
  [production]
  (transaction
   (archive-sektion production :master)
   ;; place the iso(s) in a spool directory
   (let [dam-number (prod/dam-number production)
         archive-path (.getPath (file other-spool-dir dam-number))
         multi-volume? (prod/multi-volume? production)]
     (fs/mkdir archive-path)
     ;; create the rdf
     (let [rdf-path (file archive-path (str dam-number ".rdf"))
           rdf (rdf/rdf production)]
       (spit rdf-path rdf))
     ;; copy all volumes
     (doseq [volume (range 1 (inc (:volumes production)))]
       (let [iso-archive-name (str dam-number (when multi-volume? (str "_" volume)) ".iso")
             iso-archive-path (.getPath (file archive-path "produkt" iso-archive-name))]
         (fs/copy+ (path/iso-name production volume) iso-archive-path)
         (set-file-permissions (file iso-archive-path))))
     (prod/set-state-archived! production))))
