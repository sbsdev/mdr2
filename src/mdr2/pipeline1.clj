(ns mdr2.pipeline1
  "Wrapper around the DAISY pipeline 1 scripts.

  It is assumed that there is a binary named `daisy-pipeline` on the
  path and that the pipeline scripts are installed under
  `/usr/lib/daisy-pipeline/scripts`."
  (:require [clojure.java.shell :refer [sh]]
            [clojure.string :as s]
            [me.raynes.fs :as fs]))

(def ^:private install-path "/usr/lib/daisy-pipeline/scripts")

(defn continuation-line? [line]
  (cond
   (re-find #"^\s+" line) true ; starts with white space
   (re-find #".*:$" line) true ; ends in a colon
   (= "" line) true
   :else line))

(defn- clean-line [line file]
  (-> line
      (s/replace "[ERROR, Validator]" "")
      (s/replace (str "Location: file:" file) "Line:")
      s/trim))

(defn- filter-output [output file]
  (->> output
       s/split-lines
       ;; make sure we merge continuation lines with their log line
       (partition-by continuation-line?)
       (map s/join)
       (filter #(re-matches #"^\[ERROR, Validator\].*" %))
       (map #(clean-line % file))))

(defn validate
  "Invoke the validator script of `type` for given `file`. Returns an
  empty seq on successful validation or a seq of error messages
  otherwise. Possible types are `:dtbook` or `:daisy202`"
  [file type]
  ;; the pipeline1 validator is not so brilliant when it comes to
  ;; returning error conditions. For that reason we check for
  ;; existence of the input file before hand
  (if (not (and (fs/exists? file) (fs/readable? file)))
    [(format "Input file '%s' does not exist or is not readable" file)]
    (let [args ["daisy-pipeline"
                (str install-path "/verify/ConfigurableValidator.taskScript")
                (str "--validatorInputFile=" file)]
          dtbook [;; make sure it is a DTBook file
                  "--validatorRequireInputType=Dtbook document"
                  ;; make sure files with a missing DOCTYPE declaration do not validate
                  "--validatorInputDelegates=org.daisy.util.fileset.validation.delegate.impl.NoDocTypeDeclarationDelegate"]
          daisy202 [;; make sure it is a DAISY 202 file
                    "--validatorRequireInputType=DAISY 2.02 DTB"]]
      (-> (apply sh (concat args (case type :dtbook dtbook :daisy202 daisy202)))
          :out
          (filter-output file)))))

(defn audio-encoder
  "Invoke the audio encoder script."
  [input output & {:keys [bitrate stereo freq] :as opts}]
  (let [args (merge opts {:input input :output output})]
    (apply sh "daisy-pipeline"
           (str install-path "/modify_improve/dtb/DTBAudioEncoder.taskScript")
           (map (fn [[k v]] (format "--%s=%s" (name k) v)) args))))
