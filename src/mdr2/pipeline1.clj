(ns mdr2.pipeline1
  "Wrapper around the DAISY pipeline 1 scripts.

  It is assumed that there is a binary named `daisy-pipeline` on the
  path and that the pipeline scripts are installed under
  `/usr/lib/daisy-pipeline/scripts`."
  (:use [clojure.java.shell :only [sh]])
  (:require [clojure.string :as s]))

(defn- clean-line [line file]
  (-> line
      (s/replace "[ERROR, Validator]" "")
      (s/replace (str "Location: file:" file) "Line:")
      s/trim))

(defn- filter-output [output file]
  (->> output
       s/split-lines
       (filter #(re-matches #"^\[ERROR, Validator\].*" %))
       (map #(clean-line % file))))

(defn validate
  "Invoke the validator script from for given file. Returns an empty
  seq on successful validation or a seq of error messages otherwise"
  [file]
  (-> (sh "daisy-pipeline"
          "/usr/lib/daisy-pipeline/scripts/verify/ConfigurableValidator.taskScript"
          (str "--validatorInputFile=" file)
          ;; make sure it has to be a DTBook file
          "--validatorRequireInputType=Dtbook document"
          ;; make sure files with a missing DOCTYPE declaration do not validate
          "--validatorInputDelegates=org.daisy.util.fileset.validation.delegate.impl.NoDocTypeDeclarationDelegate")
      :out
      (filter-output file)))

(defn audio-encoder
  "Invoke the audio encoder script."
  [args]
  (apply sh "daisy-pipeline"
         "/usr/lib/daisy-pipeline/scripts/modify_improve/dtb/DTBAudioEncoder.taskScript"
         (map (fn [[k v]] (format "--%s=%s" (name k) v)) args)))
