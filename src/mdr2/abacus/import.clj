(ns mdr2.abacus.import
  "Functionality for the initial import of the production data from
  ABACUS to Madras2.

  This is meant to be used only once when importing the productions
  from ABACUS. Use from within a repl."
  (:require [clojure.java.io :as io]
            [clojure.data.csv :as csv]
            [clojure.string :as string]
            [clojure.set :refer [rename-keys]]
            [clojure.java.jdbc :as jdbc]
            [environ.core :refer [env]]
            [mdr2.production :as prod]
            [immutant.transactions.jdbc :refer [factory]]))

(def ^:private db {:factory factory :name "java:jboss/datasources/productions"})
(def ^:private old-db {:factory factory :name "java:jboss/datasources/old-productions"})
(def ^:private archive-db {:factory factory :name "java:jboss/datasources/archive"})

(defn parse-int [s] (Integer/parseInt s))
(defn production-id? [id] (let [match (re-matches #"DAM (\d+)$" id)] (when match (parse-int (second match)))))
(defn library-signature? [id] (let [match (re-matches #"DS (\d+)$" id)] (when match (str "ds" (second match)))))

;; production id to library signature mapping
(def production-id-to-library-signature-map
  (reduce (fn [m {:keys [production_id library_signature]}]
            (let [k (production-id? production_id)
                  v (library-signature? library_signature)]
              (if (and k v) (assoc m k v) m)))
          {}
          (jdbc/query archive-db
                      "SELECT m.value AS production_id, m2.value AS library_signature FROM meta m, meta m2 WHERE m.id_container = m2.id_container AND m.element='idMaster' AND m2.element='idAusleihe'")))

(defn fix-production
  "Fix a `production`, i.e. fix some of the fields as they are coming
  from ABACUS the values as they are expected by Madras2"
  [{:keys [id periodical_number] :as production}]
  (let [id (parse-int (second (re-find #"dam(\d+)" id)))]
    (-> production
        (assoc :id id)
        (assoc :production_type "book")
        (assoc :periodical_number
               (when-not (string/blank? periodical_number) periodical_number))
        (assoc :library_signature (get production-id-to-library-signature-map id))
        (dissoc :Produktestatus))))

(defn productions
  "Given a file or a filename `f` return a lazy seq of productions"
  [f]
  (->> f
   io/reader
   csv/read-csv
   (drop 2) ; ignore headers
   (map #(zipmap [:product_number :creator :title :production_type
                  :source_publisher :source_date
                  :source :Produktestatus :volumes :id :multimedia_type
                  :narrator :produced_date :total_time :state :date :depth] %))
   (map prod/parse)
   (map fix-production)
   (map #(merge (prod/default-meta-data) %))))

(defn extract-id
  [line]
  (second (re-find #"^\./dam(\d+)" line)))

(defn files-by-id
  [f]
  (with-open [r (clojure.java.io/reader f)]
    (group-by extract-id (line-seq r))))

(defn contains-wav-files
  [coll]
  (some #(re-matches #".*\.wav$" %) coll))

(defn contains-struct-file
  [coll]
  (some #(re-matches #".*/struct.html$" %) coll))

(defn contains-obi-project
  [coll]
  (some #(re-matches #".*/project.obi$" %) coll))

(def abacus-csv "/home/eglic/src/mdr2/samples/ABACUS_export_Books_27032015.csv")
(def all-productions (-> abacus-csv io/file productions))
(def file-dump "/home/eglic/src/mdr2/samples/miloun_files.txt")
(def all-files-by-id (-> file-dump io/file files-by-id))

(defn to-millis
  "Convert a string `s` of the form \"31:28:47\" to milliseconds"
  [s]
  (let [[h m s] (map parse-int (string/split s #":"))]
    (* (+ (* (+ (* h 60) m) 60) s) 1000)))

(defn normalize-commercial-production
  [m]
  (-> m
      (assoc (keyword (:element m)) (:value m))
      (rename-keys {:idVorstufe :library_number
                    :producedDate :produced_date
                    :sourceDate :source_date
                    :revisionDate :revision_date
                    :sourcePublisher :source_publisher})))

(defn cleanup-commercial-production
  [m]
  (let [volumes (second (re-matches #"\d of (\d+)" (:setInfo m "1 of 1")))
        language (string/lower-case (:language m "de"))
        total_time (to-millis (:totalTime m "0:0:0"))]
    (-> m
        (assoc :production_type "book")
        (assoc :language language)
        (assoc :volumes volumes)
        (assoc :total_time total_time)
        (assoc :library_signature (get production-id-to-library-signature-map (:id m)))
        (select-keys [:id :creator :date :depth :language :library_number :library_signature
                        :narrator :process_status :produced_date :production_type
                        :revision_date :source :source_date :source_publisher
                        :subject :title :total_time :volumes]))))

(def all-commercial-productions
  (let [statement (string/join
                   "\n"
                   ["SELECT d.id, d.tm, d.identifier, d.title, d.process_status, m.element, m.value"
                    "FROM madras.document d, madras.meta m"
                    "WHERE d.id IN (SELECT id_document FROM madras.meta WHERE element='idVorstufe' AND value regexp '^PNX [2-9][0-9][0-9][0-9]')"
                    "AND d.id = m.id_document"])]
    (->> (jdbc/query old-db statement)
         (map normalize-commercial-production)
         (group-by :id)
         (map (fn [[k v]] [k (apply merge v)]))
         (map second)
         (map cleanup-commercial-production)
         (map prod/parse)
         (map #(merge (prod/default-meta-data) %)))))

(defn get-all-files
  "Get all files for a `production`"
  [{id :id}]
  (get all-files-by-id id))

(def ready-productions
  "All productions with state \"ready\""
  (filter #(= (:state %) "ready") all-productions))

(defn create-ready-productions!
  "Create all productions with state \"ready\""
  []
  (doseq [p ready-productions]
    (-> p prod/create!)))

(def archived-productions
  "All productions with state \"archived\""
  (filter #(= (:state %) "archived") all-productions))

(defn create-archived-productions!
  "Create all productions with state \"archived\""
  []
  (->> archived-productions
       (map #(assoc % :state "archived"))
       (map prod/add-default-meta-data)
       (apply jdbc/insert! db :production)))

(def recording-productions-without-wav
  "All productions with state \"recording\" that do not contain any wav file"
  (filter #(and (= (:state %) "recording")
                (not (contains-wav-files (get-all-files %)))) all-productions))

(def recording-productions-with-struct
  "All productions with state \"recording\" that do not contain any wav but a struct file"
  (filter #(contains-struct-file (get-all-files %)) recording-productions-without-wav))

(defn create-recording-productions-with-struct!
  "Create all productions with state \"recording\" that do not contain
  any wav but a struct file. Add to the db, create the directories and
  create the config file. Finally convert the \"struct.html\""
  []
  (doseq [p recording-productions-with-struct]
    (-> p
        prod/create!
        prod/set-state-structured!
        (comment
          (copy-struct-file!)
          (convert-struct-file!)))))

(def recording-productions-with-wav
  "All productions with state \"recording\" that contain wav files"
  (filter #(and (= (:state %) "recording")
                (contains-wav-files (get-all-files %)))
          all-productions))

(def recording-productions-with-obi
  "All productions with state \"recording\" with wav files that contain an obi project"
  (filter #(contains-obi-project (get-all-files %))
          recording-productions-with-wav))

(defn create-recording-productions-with-obi!
  "Create all productions with state \"recording\" with wav files that
  contain an obi project. Add to the db and create the directories.
  Copy the obi project to the new place. Place an obi config file
  inside the obi project"
  []
  (doseq [p recording-productions-with-obi]
    (-> p
        prod/create!
        prod/set-state-structured!
        (comment
          (copy-obi-project!)
          (create-obi-config-file!)))))

(def recording-productions-without-obi
  "All productions with state \"recording\" with wav files but no obi project"
  (filter #(not (contains-obi-project (get-all-files %)))
          recording-productions-with-wav))

(defn create-recording-productions-without-obi!
  "Create all productions with state \"recording\" with wav files that
  do not contain an obi project. Add to the db and create the directories"
  []
  (doseq [p recording-productions-without-obi]
    (-> p
        prod/create!
        prod/set-state-structured!
        (comment
          (copy-sigtuna-project!)))))

(defn print-stats
  "Print a summary of all the productions that are about to be migrated"
  []
  (println "Migrating productions from old to new Madras")
  (println)
  (println "Importing from ABACUS")
  (println)
  (println "Ready: " (count ready-productions))
  (println (string/join ", " (map :id ready-productions)))
  (println)
  (println "Archived: " (count archived-productions))
  (println)
  (println "Recording with no wav and a struct.html: " (count recording-productions-with-struct))
  (println (string/join ", " (map :id recording-productions-with-struct)))
  (println)
  (let [weird (filter #(not (contains-struct-file (get-all-files %))) recording-productions-without-wav)]
    (println "Recording with no wav and no struct.html: " (count weird))
    (println (string/join ", " (map :id weird))))
  (println)
  (println "Recording with wav and an obi project: " (count recording-productions-with-obi))
  (println (string/join ", " (map :id recording-productions-with-obi)))
  (println)
  (println "Recording with wav and no obi project: " (count recording-productions-without-obi))
  (println (string/join ", " (map :id recording-productions-without-obi)))
  (println)
  (println "Ignoring the rest")
  (println (dissoc (frequencies (map :state all-productions)) "archived" "recording" "ready"))
  (doseq [state ["repairing" "finishing" "recorded" "pre_ready"]]
    (println (str (string/capitalize state) ": ") (string/join ", " (map :id (filter #(= (:state %) state) all-productions)))))
  (println )
  (println "Importing commercial audio books")
  (println)
)
