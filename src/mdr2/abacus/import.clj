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
            [mdr2.dtbook :as dtbook]
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

(defn wav-file? [f] (re-matches #".*\.wav$" f))
(defn struct-file? [f] (re-matches #".*/struct.html$" f))
(defn obi-project? [f] (re-matches #".*/project.obi$" f))

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
                    :sourcePublisher :source_publisher
                    :process_status :state})))

(defn cleanup-commercial-production
  [m]
  (let [;; the quality of this data is so poor that we just assume 1
        ;; if we get garbage. See select count(*), value from meta
        ;; where element='setInfo' group by value
        volumes (or (second (re-matches #"\d of (\d+)" (:setInfo m "1 of 1")))
                    "1")
        language (string/lower-case (:language m "de"))
        total_time (to-millis (:totalTime m "0:0:0"))]
    (-> m
        (assoc :production_type "book")
        (assoc :language language)
        (assoc :volumes volumes)
        (assoc :total_time total_time)
        (assoc :library_signature (get production-id-to-library-signature-map (:id m)))
        (select-keys [:id :creator :date :depth :language :library_number :library_signature
                        :narrator :state :produced_date :production_type
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

(defn ready? [production] (= (:state production) "ready"))
(defn archived? [production] (= (:state production) "archived"))
(defn recording? [production] (= (:state production) "recording"))
(defn recording-no-wav? [production] (and (recording? production)
                                          (not-any? wav-file? (get-all-files production))))
(defn recording-no-wav-with-struct? [production] (and (recording-no-wav? production)
                                                      (some struct-file? (get-all-files production))))
(defn recording-with-wav? [production] (and (recording? production)
                                            (some wav-file? (get-all-files production))))
(defn recording-with-obi? [production] (and (recording? production)
                                            (some wav-file? (get-all-files production))
                                            (some obi-project? (get-all-files production))))
(defn recording-with-wav-no-obi? [production] (and (recording? production)
                                                   (some wav-file? (get-all-files production))
                                                   (not-any? obi-project? (get-all-files production))))

(defn create-ready-productions!
  "Create all productions with state \"ready\""
  [productions]
  (doseq [p (filter ready? productions)]
    (if (:library_number p)
      (-> p prod/create! prod/set-state-structured! dtbook/dtbook-file)
      (-> p prod/create!))))

(defn create-archived-productions!
  "Create all productions with state \"archived\""
  [productions]
  (->> (filter archived? productions)
       (map #(assoc % :state "archived"))
       (map prod/add-default-meta-data)
       (apply jdbc/insert! db :production)))

(defn create-recording-productions-with-struct!
  "Create all productions with state \"recording\" that do not contain
  any wav but a struct file. Add to the db, create the directories and
  create the config file. Finally convert the \"struct.html\""
  [productions]
  (doseq [p (filter #(recording-no-wav-with-struct? %) productions)]
    (-> p
        prod/create!
        prod/set-state-structured!
        (comment
          (copy-struct-file!)
          (convert-struct-file!)))))

(defn create-recording-productions-with-obi!
  "Create all productions with state \"recording\" with wav files that
  contain an obi project. Add to the db and create the directories.
  Copy the obi project to the new place. Place an obi config file
  inside the obi project"
  [productions]
  (doseq [p (filter recording-with-obi? productions)]
    (-> p
        prod/create!
        prod/set-state-structured!
        (comment
          (copy-obi-project!)
          (create-obi-config-file!)))))

(defn create-recording-productions-without-obi!
  "Create all productions with state \"recording\" with wav files that
  do not contain an obi project. Add to the db and create the directories"
  [productions]
  (doseq [p (filter recording-with-wav-no-obi? productions)]
    (-> p
        prod/create!
        prod/set-state-structured!
        (comment
          (copy-sigtuna-project!)))))

(defn print-stats
  "Print a summary of all the productions that are about to be migrated"
  []
  (println "Migrating productions from old to new Madras")
  (println "============================================")
  (println)
  (println "Importing from ABACUS")
  (println "~~~~~~~~~~~~~~~~~~~~~")
  (println)
  (println "Ready:" (count (filter ready? all-productions)))
  (println "-----")
  (println (string/join ", " (map :id (filter ready? all-productions))))
  (println)
  (println "Archived:" (count (filter archived? all-productions)))
  (println "--------")
  (println)
  (println "Recording with no wav and a struct.html:" (count (filter #(recording-no-wav-with-struct? %) all-productions)))
  (println "---------------------------------------")
  (println (string/join ", " (map :id (filter #(recording-no-wav-with-struct? %) all-productions))))
  (println)
  (let [weird (filter #(and (not-any? struct-file? (get-all-files %)) (recording-no-wav? %)) all-productions)]
    (println "Recording with no wav and no struct.html:" (count weird))
    (println "----------------------------------------")
    (println (string/join ", " (map :id weird))))
  (println)
  (println "Recording with wav and an obi project:" (count (filter recording-with-obi? all-productions)))
  (println "-------------------------------------")
  (println (string/join ", " (map :id (filter recording-with-obi? all-productions))))
  (println)
  (println "Recording with wav and no obi project:" (count (filter recording-with-wav-no-obi? all-productions)))
  (println "-------------------------------------")
  (println (string/join ", " (map :id (filter recording-with-wav-no-obi? all-productions))))
  (println)
  (println "Ignoring the rest")
  (println (dissoc (frequencies (map :state all-productions)) "archived" "recording" "ready"))
  (doseq [state ["repairing" "finishing" "recorded" "pre_ready"]]
    (println (str (string/capitalize state) ": ") (string/join ", " (map :id (filter #(= (:state %) state) all-productions)))))
  (println )
  (println "Importing commercial audio books")
  (println "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~")
  (println)
  (println "Ready:" (count (filter ready? all-commercial-productions)))
  (println "-----")
  (println (string/join ", " (map :id (filter ready? all-commercial-productions))))
  (println)
  (println "Archived:" (count (filter archived? all-commercial-productions)))
  (println "--------")
  (println)
)
