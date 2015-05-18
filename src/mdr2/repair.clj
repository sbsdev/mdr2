(ns mdr2.repair
  "Functions to handle repairing of a production

  This mostly entails functionality to fetch a production from the archive.

  A production is stored in the archive as a tar file. There is a HTTP
  API to the archive which is used to fetch productions. The tar file
  is then unpacked in a tmp dir inside the structured path (as there
  is not enough space in the /tmp dir). From this temporary location
  all the relevant files are copied to the structured path and the
  production is set to state \"structured\". After that the repair
  takes its course down the same route as a normal production."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [immutant.caching :as caching]
            [immutant.transactions :refer [transaction]]
            [immutant.transactions.jdbc :refer [factory]]
            [yesql.core :refer [defqueries]]
            [me.raynes.fs.compression :as compress]
            [org.tobereplaced.nio.file :as nio]
            [environ.core :refer [env]]
            [clj-http.client :as client]
            [mdr2.obi :as obi]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]
            [mdr2.util :as util]))

(def ^:private db {:factory factory :name "java:jboss/datasources/archive"})
(def ^:private archive-web-root (env :archive-web-root))
(def ^:private archive-web-user (env :archive-web-user))
(def ^:private archive-web-password (env :archive-web-password))

(defqueries "mdr2/archive/queries.sql" {:connection db})

(def ^:private repairing-cache (caching/cache "repairing-cache" :ttl [15 :minutes]))

(defn production-id?
  "Return true if `id` is a valid production id"
  [id]
  (re-matches #"(?i)dam\d{1,5}" id))

(defn product-number?
  "Return true if `id` is a valid product number"
  [id]
  (re-matches #"DY\d{5}" id))

(defn archive-identifier?
  "Return true if `id` is either a valid production id, i.e. a dam
  number or a valid library signature"
  [id]
  (or (production-id? id) (prod/library-signature? id)))

(defn archive-url
  "Return the url for an archived production given a `container-id`"
  [container-id]
  (format "%s/0%s/%s/a0%s.tar" archive-web-root
          (.substring container-id 0 1)
          (.substring container-id 1 3)
          container-id))

(defn container-id
  "Return a container-id given a `production` and optionally `sektion`
  which can be either `:master` or `:dist-master`"
  ([production]
   (container-id production :master))
  ([production sektion]
   (let [id (case sektion
              :master (prod/dam-number production)
              :dist-master (:library_signature production))]
     (-> {:id id} production-id-to-archive-id first :id str))))

(defn repair
  "Get a production from the archive and prepare it for repairing"
  [{id :id :as production}]
  (let [repairing-in-progress? (.putIfAbsent repairing-cache id true)
        dest-path (path/structured-path production)
        ;; create the tmp dir in the structured-path as there is not
        ;; enough space on the normal tmp dir path
        tmp-dir (nio/resolve-sibling dest-path (str id "_repair"))]
    (cond
      repairing-in-progress?
      (let [error-msg (format "Repair for %s already pending" id)]
        (log/warn error-msg)
        [error-msg])
      (not= (:state production) "archived")
      (let [error-msg "Production is not in state \"archived\""]
        (log/error error-msg)
        [error-msg])
      ;; checking for the state is not enough: there is a race
      ;; condition as the state is only set after all the files have
      ;; been copied. This takes time. So we also check for the
      ;; existence of some important directories
      (nio/exists? dest-path)
      (let [error-msg (format "Directory %s already exists" dest-path)]
        (log/error error-msg)
        [error-msg])
      (nio/exists? tmp-dir)
      (let [error-msg (format "Directory %s already exists" tmp-dir)]
        (log/error error-msg)
        [error-msg])
      :else
      (let [container-id (container-id production)
            url (archive-url container-id)
            response (client/get url
                                 {:as :stream
                                  :basic-auth [archive-web-user archive-web-password]})]
        (log/infof "Repairing %s" id)
        (if (client/success? response)
          (let [dam-number (prod/dam-number production)
                tar-file (io/file tmp-dir (str dam-number ".tar"))
                tar-dir (io/file tmp-dir dam-number)]
            (transaction
             ;; create all dirs for this production
             (prod/create-dirs production)
             ;; copy tar to tmpdir
             (nio/create-directory! tmp-dir)
             (with-open [input (:body response)
                         output (io/output-stream tar-file)]
               (io/copy input output))
             ;; extract the tar
             (compress/untar tar-file tar-dir)
             ;; extract the relevant parts to the structured-path
             (let [src-path (io/file tar-dir dam-number "produkt" dam-number)]
               (doseq [f (filter #(.isFile %) (file-seq src-path))]
                 (nio/move! f (io/file dest-path (nio/file-name f)))))
             ;; clear the temporary files
             (util/delete-directory! tmp-dir)
             ;; create the obi config file
             (obi/config-file production)
             ;; set the state
             (let [updated (prod/set-state! production "structured")]
               ;; clear repairing-cache
               (.remove repairing-cache id)
               updated)
             (prod/set-state! production "structured")))
          (let [error-msg (format "Couldn't get %s from archive (%s)" id url)]
            ;; close the stream
            (.close (:body response))
            (log/error error-msg)
            (.remove repairing-cache id)
            [error-msg]))))))

