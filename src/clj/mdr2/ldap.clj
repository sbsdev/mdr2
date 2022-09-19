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
                   (remove nil?))
        roles (conj roles "madras2.admin")] ; FIXME: this is a temporary workaround
    (assoc user :roles (apply hash-set roles))))

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
            (select-keys [:uid :mail :initials :givenName :displayName :telephoneNumber :roles])))
      (finally (ldap/release-connection ldap-pool conn)))))
