(ns mdr2.state
  "Functionality for productions"
  (:require [clojure.set :as set]
            [clojure.string :as s]))

(def states
  "All possible states that a production can have"
  [:new :structured :recorded :cataloged :archived :pending-volume-split :failed :deleted])

(def states-map
  "Map of states to state number to facilitate lookup from state kw to
  state number"
  (zipmap states (range)))

(def states-inverse
  "Inverse map to facilitate lookup from state number to state name"
  (set/map-invert states-map))

(def initial-state :new)

(def states-graph
  {:new [:structured]
   :structured [:recorded]
   :archived [:structured]})

(defn to-int 
  "Return the state number for the given `state`"
  [state]
  (get states-map state))

(defn to-str 
  "Return a capitalized state name for given `state`"
  [state]
  (s/capitalize (name state)))

(defn from-int 
  "Return a state for given state number. If there is no state for
  given number return the new state"
  [i]
  (get states-inverse i :new))

(defn next-states
  "For given `state` return a seq of possible next states"
  [state]
  (get states-graph state))
