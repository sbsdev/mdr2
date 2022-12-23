(ns mdr2.productions.drag-and-drop
  (:require [re-frame.core :as rf]))

;; see https://developer.mozilla.org/en-US/docs/Web/API/HTML_Drag_and_Drop_API/File_drag_and_drop

(defn handle-drag-over [event]
  (.preventDefault event)
  (set! (.-dropEffect (.-dataTransfer event)) "copy"))

(defn handle-on-drop [event handler]
  (.preventDefault event)
  (when-let [files (-> event .-dataTransfer .-files)]
    (when-let [candidate (->> files
                              seq
                              ;; consider only xml files
                              (filter #(= (str (.-type %)) "text/xml"))
                              first)]
      (handler candidate))))
