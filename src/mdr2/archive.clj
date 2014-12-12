(ns mdr2.archive
  "Main entry point into the archive

For archiving we need to interface with an existing hellish system
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

FIXME: Most likely this should be split off into a separate lib that
replaces all of agadir at a later point in time. For instructions on
how to do this see
https://github.com/technomancy/leiningen/blob/stable/doc/DEPLOY.md"
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

(defn container-id
  ;; FIXME: this needs to be handled differently. The master needs to
  ;; be archived with a dam-number and the distribution master, i.e.
  ;; the iso files need to be archived with their library signature,
  ;; i.e. ds number
  "Return the name of a archive spool directory for a given production"
  [{id :id :as production}]
  (cond
   (prod/iso? production) (str "ds" id) ; if it has an iso it is supposed to be called "ds"
   :else (prod/dam-number production)))

(defn container-path
  "Return the path to the archive spool directory for a given production"
  [production]
  (let [id (container-id production)]
    (.getPath (file spool-dir id))))

(defn container-rdf-path
  "Return the path to the rdf file in the archive spool for a given production"
  [production]
  (let [id (container-id production)
        rdf-name (str id ".rdf")]
    (.getPath (file spool-dir id rdf-name))))

(defn add-to-db
  "Insert a production into the archive db. This marks the files in
  the spool directory as ready for archiving and concludes the
  archiving process from the point of view of the production system."
  [production & {sektion :sektion}]
  (let [new-job
        {:verzeichnis (container-id production)
         ;; :id (container-id production)
         ;; :verzeichnis (container-path production)
         :sektion sektion}
        job (merge default-job new-job)]
    (jdbc/insert! db :container job)))

(defn copy-master-files
  "Copy a production master, i.e. the DAISY3 with wav files, to the
  archive spool dir"
  [production]
  (let [archive-path (container-path production)]
    (if (fs/exists? archive-path)
      (log/error "Container-path %s already exists" archive-path)
      (fs/copy-dir (path/recorded-path production) (container-path production)))))

(defn copy-distribution-master-files
  "Copy a production to the archive spool dir"
  ;; FIXME: this fails if there is already an archiving in progress
  ;; because it will create another copy inside the already existing
  ;; dam directory
  [production]
  (let [archive-path (container-path production)
        ;; FIXME: This will fail in the light of multiple isos
        iso-archive-name (str (container-id production) ".iso")
        iso-archive-path (.getPath (file archive-path iso-archive-name))]
    (fs/copy+ (path/iso-name production) iso-archive-path)))

(defn create-rdf
  "Create an rdf file and place it in the archive spool directory"
  [production]
  (let [rdf (rdf/rdf production)
        rdf-path (container-rdf-path production)]
    (spit rdf-path rdf)))

(defn archive-master
  "Archive a master"
  [production]
  ;; place all the files in the spool dir
  (copy-master-files production)
  ;; create an rdf file
  (create-rdf production)
  ;; add it to the db so that the agadir machinery will pick it up
  (add-to-db production :sektion "master"))

(defn archive-distribution-master
  "Archive a distribution master, .i.e. a DTB encoded with mp3 and
  packed up in one or more iso files"
  [production]
  ;; place all the files in the spool dir
  (copy-distribution-master-files production)
  ;; create an rdf file
  (create-rdf production)
  ;; add it to the db so that the agadir machinery will pick it up
  (add-to-db production :sektion "cdimage")
  ;; remove iso and mp3s
  (encode/clean-up))

(defn archive
  "Archive a production"
  [production]
  (archive-master production)
  (archive-distribution-master production))
