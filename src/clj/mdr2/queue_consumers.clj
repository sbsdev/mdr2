(ns mdr2.queue-consumers
  ;; This is a helper namespace to avoid a circular dependency
  ;; production -> queues -> production. By splitting the queues into
  ;; queues and queue-consumers we can avoid this. The consumers are
  ;; mounted in mdr2.core
  (:require
   [mdr2.archive.async]
   [mdr2.repair.async]
   [mdr2.encode.async]
   [mdr2.abacus.async]))

