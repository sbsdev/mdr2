(ns mdr2.utils)

(defn is-admin? [{:keys [roles] :as user}]
  (contains? roles "madras2.admin"))
