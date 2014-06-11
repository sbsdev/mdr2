(ns mdr2.pipeline1
  (:use [clojure.java.shell :only [sh]])
  (:require [clojure.string :as s]))

(defn validate [file]
  (sh "daisy-pipeline"
      "/usr/lib/daisy-pipeline/scripts/verify/ConfigurableValidator.taskScript"
      (str "--validatorInputFile=" file)
      ;; make sure it has to be a DTBook file
      "--validatorRequireInputType=Dtbook document"
      ;; make sure files with a missing DOCTYPE declaration do not validate
      "--validatorInputDelegates=org.daisy.util.fileset.validation.delegate.impl.NoDocTypeDeclarationDelegate"))

(defn audio-encoder [args]
  (apply sh "daisy-pipeline"
         "/usr/lib/daisy-pipeline/scripts/modify_improve/dtb/DTBAudioEncoder.taskScript"
         (map (fn [[k v]] (format "--%s=%s" (name k) v)) args)))

(defn filter-output [lines]
  (map #(s/trim (s/replace % "[ERROR, Validator]" ""))
       (filter #(re-matches #"^\[ERROR, Validator\].*" %)
               (s/split-lines lines))))

