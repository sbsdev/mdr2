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
            [clojure.java.shell :refer [sh]]
            [me.raynes.fs :as fs]
            [mdr2.rdf :as rdf]
            [mdr2.pipeline1 :as pipeline]))

(def ^:private db {:subprotocol "sqlite" 
                   :subname "db/archive.db"})

(def spool-dir
  "Path to the archive spool directory, i.e. where to place incoming
  productions that are to be archived"
   "/var/spool/agadir")

(def ^:private default-job
  {:verzeichnis ""
   :archivar "NN"
   :abholer "NN"
   :aktion "save"
   :flags "x"
   :transaktions_status "pending"})

(defn container-id 
  "Return the name of a archive spool directory for a given production"
  [{:keys [id]}]
  (str "dam" id))

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
  [production]
  (let [new-job 
        {:verzeichnis (container-id production)
         ;; :id (container-id production)
         ;; :verzeichnis (container-path production)
         ;; if there is a cdimage attached to this production then we
         ;; need to add some magic incantations to get this properly
         ;; archived
         :sektion (if (:iso-path production) "cdimage" "master")}
        job (merge default-job new-job)]
    (jdbc/insert! db :container job)))

(defn encode-production
  "Encode a production, i.e. convert the wav files to mp3"
  [{:keys [path] :as production}]
  (let [tmp-path (.getPath (fs/temp-dir "mdr2"))]
    (pipeline/audio-encoder {:input path :output tmp-path})
    (assoc production :encoded-path tmp-path)))

(defn create-iso 
  "Pack a production in an iso file. 
Return a map with an additional key `iso-path` where the iso is located"
  [{:keys [encoded-path title publisher] :as production}]
  (let [iso-path (.getPath (file (fs/tmpdir) (fs/temp-name "mdr2" ".iso")))]
    (sh "genisoimage" 
        "-quiet" 
        "-r" 
        "-publisher" publisher
        "-V" title ; volume ID (volume name or label)
        "-J" ; Generate Joliet directory records in addition to regular ISO9660 filenames.
        "-o" iso-path encoded-path)
    (assoc production :iso-path iso-path)))

(defn copy-files 
  "Copy a production to the archive spool dir"
  [{:keys [path iso-path] :as production}]
  (let [archive-path (container-path production)]
    ;; if the production has an iso archive that, otherwise just
    ;; archive the raw unencoded files
    (if iso-path 
      (fs/copy+ iso-path archive-path)
      (fs/copy-dir path archive-path)))
  production)

(defn create-rdf 
  "Create an rdf file and place it in the archive spool directory"
  [production]
  (let [rdf (rdf/rdf production)
        rdf-path (container-rdf-path production)]
    (spit rdf-path rdf))
  production)

(defn clean-up-tmp-files
  "Clean up temporary files of a production, namely encoded-path and iso-path"
  [{:keys [encoded-path iso-path] :as production}]
  (doseq [path [encoded-path iso-path]]
    (fs/delete-dir path))
  (dissoc production :encoded-path :iso-path))

(defn archive-master
  "Archive a master"
  [production]
  (-> production
      copy-files ; place all the files in the spool dir
      create-rdf ; create an rdf file
      add-to-db ; add it to the db so that the agadir machinery will pick it up
      clean-up-tmp-files))

(defn archive-distribution-master 
  "Archive a distribution master .i.e. a DTB encoded with mp3 and packed up in one or more iso files"
  [production]
  (-> production
      encode-production ; encode the dtb
      create-iso ; pack it in an iso
      copy-files ; copy the files to the spool dir
      create-rdf ; create an rdf file
      add-to-db ; add it to the db
      clean-up-tmp-files))

(defn archive
  "Archive a production"
  [production]
  (archive-master production)
  (archive-distribution-master production))
  
