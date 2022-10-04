(ns mdr2.repair.core
  "Functions to handle repairing of a production

  This mostly entails functionality to fetch a production from the archive.

  A production is stored in the archive as a tar file. There is a HTTP
  API to the archive which is used to fetch productions. The tar file
  is then unpacked in a tmp dir inside the structured path (as there
  is not enough space in the /tmp dir). From this temporary location
  all the relevant files are copied to the structured path and the
  production is set to state \"structured\". After that the repair
  takes its course down the same route as a normal production."
  (:require
   [babashka.fs :as fs]
   [clj-http.client :as client]
   [clojure.java.io :as io]
   [clojure.java.shell :as shell]
   [clojure.tools.logging :as log]
   [mdr2.config :refer [env]]
   [mdr2.db.core :as db]
   [mdr2.obi :as obi]
   [mdr2.production :as prod]
   [mdr2.production.path :as path]
   [iapetos.collector.fn :as prometheus]
   [mdr2.metrics :as metrics]))

(defn production-id?
  "Return true if `id` is a valid production id"
  [id]
  (re-matches #"(?i)dam\d{1,5}" id))

(defn product-number?
  "Return true if `id` is a valid product number"
  [id]
  (re-matches #"DY\d{1,5}" id))

(defn archive-url
  "Return the url for an archived production given a `container-id`"
  [container-id]
  ;; zero pad the container-id so that it contains 6 digits
  (let [padded-id (format "%06d" container-id)]
    (format "%s/%s/%s/a%s.tar" (env :archive-web-root)
            (.substring padded-id 0 2)
            (.substring padded-id 2 4)
            padded-id)))

(defn container-id
  "Return a container-id given a `production` and optionally `sektion`
  which can be either `:master` or `:dist-master`"
  ([production]
   (container-id production :master))
  ([production sektion]
   (let [id (case sektion
              :master (prod/dam-number production)
              :dist-master (:library_signature production))]
     (-> {:id id} db/production-id-to-archive-id :id))))

(defn repair
  "Get a production from the archive and prepare it for repairing"
  [{id :id :as production}]
  (let [dest-path (path/structured-path production)
        ;; create the tmp dir in the structured-path as there is not
        ;; enough space on the normal tmp dir path
        tmp-dir (fs/path (fs/parent dest-path) (str id "_repair"))]
    (cond
      (not= (:state production) "archived")
      (throw (ex-info "Production is not in state \"archived\"" {:error-id :invalid-state}))
      ;; checking for the state is not enough: there is a race
      ;; condition as the state is only set after all the files have
      ;; been copied. This takes time. So we also check for the
      ;; existence of some important directories
      (fs/exists? dest-path)
      (throw (ex-info (format "Directory %s already exists" dest-path) {:error-id ::directory-already-exists}))
      (fs/exists? tmp-dir)
      (throw (ex-info (format "Directory %s already exists" tmp-dir) {:error-id ::directory-already-exists}))
      :else
      ;; rollback to the archived state if any exceptions occur in the process of repairing. But at
      ;; the same time we want others to see that we are in the process of repairing, i.e. we want
      ;; to have an isolation level of :none. Unfortunately that is not supported by MySQL, so we
      ;; have to resort to a "manual" transaction using try/catch.
      ;;      (conman/with-transaction [db/*db* {:isolation :none}]
      (try
        (let [production (prod/set-state! production "repairing")
              container-id (container-id production)
              url (archive-url container-id)
              response (client/get url
                                   {:as :stream
                                    :basic-auth [(env :archive-web-user) (env :archive-web-password)]})]
          (log/infof "Repairing %s" id)
          (if (client/success? response)
            (let [dam-number (prod/dam-number production)
                  tar-file (fs/path tmp-dir (str dam-number ".tar"))]

              ;; fail fast if any of the filesystem operations fail. Where filesystem operations
              ;; silently ignore failures, we check the results and throw an exception.
              ;;create all dirs for this production
              (prod/create-dirs production)
              ;; copy tar to tmpdir
              (fs/create-dirs tmp-dir)
              (with-open [input (:body response)]
                (io/copy input (fs/file tar-file)))
              ;; extract the tar
              (let [{exit :exit} (shell/sh "tar" "--extract" (format "--file=%s" tar-file) :dir (fs/file tmp-dir))]
                (if (= exit 0)
                  (do
                    ;; extract the relevant parts to the structured-path
                    (let [src-path (fs/file tmp-dir dam-number "produkt" dam-number)]
                      (doseq [f (filter #(.isFile %) (file-seq src-path))]
                        (fs/move f (io/file dest-path (fs/file-name f)))))
                    ;; clear the temporary files
                    (if-not (fs/delete-tree tmp-dir)
                      ;; we probably do not need to roll back the state just because we couldn't
                      ;; clean up some temporary files
                      (throw (ex-info (format "Failed to remove temporary files in %s" tmp-dir) {:error-id ::tmp-file-remove-failed}))
                      (do
                        ;; create the obi config file
                        (obi/config-file production)
                        ;; set the state
                        (prod/set-state! production "structured"))))
                  ;; if the extracting of the tar failed throw an exception which will roll back the
                  ;; transaction and undo all changes in the database
                  (throw (ex-info (format "Failed to extract the tar file %s inside %s" tar-file tmp-dir) {:error-id ::tar-extract-failed})))))
            (do
              (.close (:body response)) ;; close the stream
              (throw (ex-info (format "Couldn't get %s from archive (%s)" id url) {:error-id ::archive-fetch-failed})))))
        (catch Exception e
          (prod/set-state! production "archived")
          (throw e))))))

(prometheus/instrument! metrics/registry #'repair)
