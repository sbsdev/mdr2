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
            [environ.core :refer [env]]
            [me.raynes.fs :as fs]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.encode :as encode]
            [mdr2.rdf :as rdf]
            [mdr2.pipeline1 :as pipeline]))

(def ^:private db (env :archive-database-url))

(def spool-dir
  "Path to the archive spool directory, i.e. where to place incoming
  productions that are to be archived"
   (env :archive-spool-dir))

(def ^:private default-job
  {:verzeichnis ""
   :archivar "NN"
   :abholer "NN"
   :aktion "save"
   :flags "x"
   :transaktions_status "pending"})

(defn- container-id
  "Return the name of a archive spool directory for a given
  `production` and `sektion`"
  ([production sektion]
   (container-id production sektion nil))
  ([production sektion volume]
   (case sektion
     :master (prod/dam-number production)
     :dist-master (str "ds" (:library_signature production)
                       (when (and volume
                                  (prod/multi-volume? production))
                         (str "_" volume))))))

(defn- container-path
  "Return the path to the archive spool directory for a given
  `production` and `sektion`"
  [production sektion]
  (let [id (container-id production sektion)]
    (.getPath (file spool-dir id))))

(defn- container-rdf-path
  "Return the path to the rdf file in the archive spool for a given
  `production` and `sektion`"
  ([production sektion]
   (container-rdf-path production sektion nil))
  ([production sektion volume]
   (let [path (container-id production sektion)
         rdf-name (str (container-id production sektion volume) ".rdf")]
     (.getPath (file spool-dir path rdf-name)))))

(defn- add-to-db
  "Insert a `production` into the archive db for the given `sektion`.
  This marks the files in the spool directory as ready for archiving
  and concludes the archiving process from the point of view of the
  production system."
  [production sektion]
  (let [volumes (inc (:volumes production))
        entries (case sektion :master 2 :dist-master volumes)]
    (doseq [volume (range 1 entries)]
      (let [new-job
            {:verzeichnis (container-id production sektion volume)
             :sektion (case sektion :master "master" :dist-master "cdimage")}
            job (merge default-job new-job)]
        (jdbc/insert! db :container job)))))

(defn- copy-files
  "Copy a `production` to the archive spool dir for the given
  `sektion`. For a production master copy the whole DTB including wav
  files. For a production distribution master copy the isos"
  [production sektion]
  (let [archive-path (container-path production sektion)]
    (if-not (fs/exists? archive-path)
      (case sektion
        :master
        (fs/copy-dir (path/recorded-path production) archive-path)
        :dist-master
        (doseq [volume (range 1 (inc (:volumes production)))]
          (let [iso-archive-name (str (container-id production sektion volume) ".iso")
                iso-archive-path (.getPath (file archive-path iso-archive-name))]
            (fs/copy+ (path/iso-name production volume) iso-archive-path))))
      (log/error "Archive path %s already exists" archive-path))))

(defn- create-rdf
  "Create an rdf file and place it in the appropriate archive spool
  directory"
  [production sektion]
  (let [rdf (rdf/rdf production)
        entries (case sektion :master 2 :dist-master (inc (:volumes production)))]
    (doseq [volume (range 1 entries)]
      (let [rdf-path (container-rdf-path production sektion volume)]
        (spit rdf-path rdf)))))

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

(defn archive
  "Archive a `production`"
  [production]
  (archive-sektion production :master)
  (archive-sektion production :dist-master))
