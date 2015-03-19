(ns mdr2.repair
  "Functions to handle repairing of a production

  This mostly entails functionality to fetch a production from the archive."
  (:require [clojure.java.io :as io]
            [clojure.tools.logging :as log]
            [yesql.core :refer [defqueries]]
            [me.raynes.fs :as fs]
            [me.raynes.fs.compression :as compress]
            [org.tobereplaced.nio.file :as nio]
            [environ.core :refer [env]]
            [clj-http.client :as client]
            [mdr2.obi :as obi]
            [mdr2.production :as prod]
            [mdr2.production.path :as path]))

(def ^:private db (env :archive-database-url))
(def ^:private archive-web-root (env :archive-web-root))
(def ^:private archive-web-user (env :archive-web-user))
(def ^:private archive-web-password (env :archive-web-password))

(defqueries "mdr2/archive/queries.sql" {:connection db})

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
  [production]
  (let [container-id (container-id production)
        url (archive-url container-id)
        response (client/get url
                  {:as :stream
                   :basic-auth [archive-web-user archive-web-password]})]
    (if (client/success? response)
      (let [dam-number (prod/dam-number production)
            tar-file (io/file (fs/tmpdir) (str dam-number ".tar"))
            tar-dir (io/file (fs/tmpdir) dam-number)]
        ;; create all dirs for this production
        (prod/create-dirs production)
        ;; copy tar to tmpdir
        (with-open [input (:body response)
                    output (io/output-stream tar-file)]
          (io/copy input output))
        ;; extract the tar
        (compress/untar tar-file tar-dir)
        ;; extract the relevant parts to the structured-path
        (let [src-path (io/file tar-dir dam-number "produkt" dam-number)
              dest-path (path/structured-path production)]
          (doseq [f (filter #(.isFile %) (file-seq src-path))]
            (nio/move! f (io/file dest-path (fs/base-name f)))))
        ;; clear the temporary files
        (fs/delete tar-file)
        (fs/delete-dir tar-dir)
        ;; create the obi config file
        (obi/config-file production)
        ;; set the state
        (prod/set-state! production "structured"))
      (do
        (log/errorf "Couldn't get %s from archive (%s)" (:id production) url)
        ;; close the stream
        (.close (:body response))))))

