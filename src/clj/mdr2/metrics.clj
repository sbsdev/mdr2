(ns mdr2.metrics
  (:require
   [iapetos.core :as prometheus]
   [iapetos.collector.jvm :as jvm]
   [iapetos.collector.fn :as fn]
   [iapetos.collector.ring :as ring]))

(defonce registry
  (-> (prometheus/collector-registry)
      (jvm/initialize)
      (fn/initialize)
      (ring/initialize
       ;; we have some requests that take a long time, so we extend the default buckets
       {:latency-histogram-buckets [0.001 0.005 0.01 0.02 0.05 0.1 0.2 0.3 0.5 0.75 1 5 10]})))
