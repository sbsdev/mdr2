(ns mdr2.ldap
  (:require [clj-ldap.client :as ldap]
            [clojure.tools.logging :as log]
            [mdr2.config :refer [env]]
            [mount.core :refer [defstate]]))

(defstate ldap-pool
  :start
  (if-let [address (env :ldap-address)]
    (ldap/connect
     {:host
      {:address address
       :port 389
       :connect-timeout (* 1000 5)
       :timeout (* 1000 30)}})
    (log/warn "LDAP bind address not found, please set :ldap-address in the config file"))
  :stop
  (when ldap-pool
    (ldap/close ldap-pool)))

(defn- extract-group [s]
  (->> s
   (re-matches #"cn=(\w+),cn=groups,cn=accounts,dc=sbszh,dc=ch")
   second))

(defn- extract-role [s]
  (->> s
   ;; only extract roles related to Madras2
   (re-matches #"cn=madras2.([a-z_.]+),cn=roles,cn=accounts,dc=sbszh,dc=ch")
   second))

(defn- add-groups [{memberships :memberOf :as user}]
  (let [groups (->> memberships
                   (map extract-group)
                   (remove nil?))]
    (assoc user :groups groups)))

(defn- add-roles [{memberships :memberOf :as user}]
  (let [roles (->> memberships
                   (map extract-role)
                   (map keyword)
                   (remove nil?)
                   set)]
    (assoc user :roles roles)))

(defn- not-empty-roles
  "Return the given `user` if it has any roles, otherwise return nil"
  [{:keys [roles] :as user}]
  (when (not-empty roles) user))

(defn authenticate [username password & [attributes]]
  (let [conn           (ldap/get-connection ldap-pool)
        qualified-name (str "uid=" username ",cn=users,cn=accounts,dc=sbszh,dc=ch")]
    (try
      (if (ldap/bind? conn qualified-name password)
        (-> conn
            (ldap/search "cn=users,cn=accounts,dc=sbszh,dc=ch"
                         {:filter (str "uid=" username)
                          :attributes (or attributes [])})
            first
            add-roles
            (select-keys [:uid :mail :initials :givenName :displayName :telephoneNumber :roles])
            not-empty-roles)) ;; only return users that have a role
      (finally (ldap/release-connection ldap-pool conn)))))
