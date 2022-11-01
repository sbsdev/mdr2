(ns mdr2.routes.services
  (:require
    [reitit.swagger :as swagger]
    [reitit.swagger-ui :as swagger-ui]
    [reitit.ring.coercion :as coercion]
    [reitit.coercion.spec :as spec-coercion]
    [spec-tools.data-spec :as spec]
    [reitit.ring.middleware.muuntaja :as muuntaja]
    [reitit.ring.middleware.multipart :as multipart]
    [reitit.ring.middleware.parameters :as parameters]
    [mdr2.middleware :refer [wrap-restricted wrap-authorized]]
    [mdr2.middleware.formats :as formats]
    [ring.util.http-response :refer :all]
    [clojure.string :refer [blank?] :as string]
    [clojure.java.io :as io]
    [mdr2.db.core :as db]
    [mdr2.auth :as auth]
    [mdr2.abacus.core :as abacus]
    [mdr2.dtbook :as dtbook]
    [mdr2.vubis :as vubis]
    [mdr2.production :as prod]
    [mdr2.production.spec :as prod.spec]
    [mdr2.pipeline1 :as pipeline]
    [mdr2.repair.core :as repair]
    [mdr2.dtbook.validation :as validation]))

(def default-limit 100)

(defn service-routes []
  ["/api"
   {:coercion spec-coercion/coercion
    :muuntaja formats/instance
    :swagger {:id ::api
              :securityDefinitions {:apiAuth
                                    {:type "apiKey"
                                     :name "Authorization"
                                     :in "header"}}}
    :middleware [;; query-params & form-params
                 parameters/parameters-middleware
                 ;; content-negotiation
                 muuntaja/format-negotiate-middleware
                 ;; encoding response body
                 muuntaja/format-response-middleware
                 ;; exception handling
                 coercion/coerce-exceptions-middleware
                 ;; decoding request body
                 muuntaja/format-request-middleware
                 ;; coercing response bodys
                 coercion/coerce-response-middleware
                 ;; coercing request parameters
                 coercion/coerce-request-middleware
                 ;; multipart
                 multipart/multipart-middleware]}

   ;; swagger documentation
   ["" {:no-doc true
        :swagger {:info {:title "Madras2"
                         :description "https://cljdoc.org/d/metosin/reitit"}}}

    ["/swagger.json"
     {:get (swagger/create-swagger-handler)}]

    ["/api-docs/*"
     {:get (swagger-ui/create-swagger-ui-handler
             {:url "/api/swagger.json"
              :config {:validator-url nil}})}]]

   ["/login"
    {:post
     {:summary    "Handle user login"
      :tags       ["Authentication"]
      :parameters {:body {:username string?
                          :password string?}}
      :handler    (fn [{{{:keys [username password]} :body} :parameters}]
                    (if-let [credentials (auth/login username password)]
                      (ok credentials) ; return token and user info
                      (bad-request
                       {:message "Cannot authenticate user with given password"})))}}]

   ["/productions"
    {:swagger {:tags ["Productions"]}}

    [""
     {:get {:summary "Get all productions"
            :description "Get all productions. Optionally limit the result set using a `search` term, a `limit` and an `offset`. If a `state` is given only return productions with that state, otherwise return all productions that are not 'archived' or 'deleted'"
            :parameters {:query {(spec/opt :search) string?
                                 (spec/opt :state) string?
                                 (spec/opt :limit) int?
                                 (spec/opt :offset) int?}}
            :handler (fn [{{{:keys [limit offset search state]
                             :or {search "" limit default-limit offset 0}} :query} :parameters}]
                       (let [params (cond
                                      (repair/production-id? search) {:id (subs search 3)} ;; DAM123
                                      (prod/library-signature? search) {:library_signature search} ;; ds12345
                                      (repair/product-number? search) {:product_number search} ;; DY123
                                      :else {:search (if (blank? search) nil (db/search-to-sql search))})
                             params (merge {:state state :limit limit :offset offset} params)]
                         (->> (db/get-productions params)
                              (map prod/remove-null-values)
                              ok)))}
      :post {:summary "Create a production"
             :middleware [wrap-restricted wrap-authorized]
             :swagger {:security [{:apiAuth []}]}
             :authorized #{:it}
             :parameters {:body prod.spec/production}
             :handler (fn [{{p :body} :parameters}]
                        (try
                          (let [p (prod/create! p)]
                            (created (str (:id p))))
                          (catch clojure.lang.ExceptionInfo e
                            (let [{:keys [error-id errors]} (ex-data e)]
                              (case error-id
                                :duplicate-key (bad-request {:status-text (ex-message e)})
                                (internal-server-error {:status-text (ex-message e)}))))))}}]

    ["/:id"
     {:get {:summary "Get a production by ID"
            :parameters {:path {:id int?}}
            :handler (fn [{{{:keys [id]} :path} :parameters}]
                       (if-let [doc (prod/get-production id)]
                         (ok doc)
                         (not-found)))}

      :delete {:summary "Delete a production"
               :description "Mark a production as deleted"
               :middleware [wrap-restricted wrap-authorized]
               :swagger {:security [{:apiAuth []}]}
               :authorized #{:it}
               :parameters {:path {:id int?}}
               :handler (fn [{{{:keys [id]} :path} :parameters}]
                          (if-let [p (prod/get-production id)]
                            (try
                              (prod/delete! p)
                              (no-content)
                              (catch clojure.lang.ExceptionInfo e
                                (internal-server-error {:status-text (ex-message e)})))
                            (not-found)))}

      :patch {:summary "Patch a production, e.g. update the library_signature"
              :middleware [wrap-restricted wrap-authorized]
              :swagger {:security [{:apiAuth []}]}
              :authorized #{:catalog :it}
              :parameters {:path {:id int?}
                           :body {:library_signature ::prod.spec/library_signature}}
              :handler (fn [{{{:keys [id]} :path {:keys [library_signature]} :body} :parameters}]
                         (let [p (prod/get-production id)]
                           (cond
                             (nil? p) (not-found)
                             (not= (:state p) "encoded") (conflict {:status-text "Only encoded productions can be assigned a library signature"})
                             :else (try
                                     (prod/set-state-cataloged! (assoc p :library_signature library_signature))
                                     (no-content)
                                     (catch clojure.lang.ExceptionInfo e
                                       (internal-server-error {:status-text (ex-message e)}))))))}}]

    ["/:id/repair"
     {:post {:summary "Repair a production"
             :middleware [wrap-restricted wrap-authorized]
             :swagger {:security [{:apiAuth []}]}
             :authorized #{:admin :studio :it}
             :parameters {:path {:id int?}}
             :handler (fn [{{{:keys [id]} :path} :parameters
                            {user :user} :identity}]
                        (let [p (prod/get-production id)]
                          (cond
                            (nil? p) (not-found)
                            (not= (:state p) "archived") (conflict {:status-text "Only archived productions can be repaired"})
                            :else (try
                                    (prod/repair! (assoc p :repair/initiated-by user))
                                    (no-content)
                                    (catch clojure.lang.ExceptionInfo e
                                      (internal-server-error {:status-text (ex-message e)}))))))}}]
    ["/:id/split"
     {:post {:summary "Mark a production as split, i.e. ready to be encoded as a manually split production"
             :middleware [wrap-restricted wrap-authorized]
             :swagger {:security [{:apiAuth []}]}
             :authorized #{:admin :it}
             :parameters {:path {:id int?}
                          :body {:volumes ::prod.spec/volumes
                                  :sample-rate ::prod.spec/sample-rate
                                  :bit-rate ::prod.spec/bit-rate}}
             :handler (fn [{{{:keys [id]} :path
                             {:keys [volumes sample-rate bit-rate]} :body} :parameters
                            {user :user} :identity}]
                        (let [p (prod/get-production id)]
                          (cond
                            (nil? p) (not-found)
                            (not= (:state p) "pending-split")
                            (conflict {:status-text
                                       "Only productions in state \"pending-split\" can be split"})
                            :else
                            ;; check if the split production is even valid. Assume that we are
                            ;; going to have at least two volumes
                            (let [errors (prod/manifest-validate
                                          (cond-> p (prod/multi-volume? p) (assoc :volumes 2)))]
                              (if (seq errors)
                                ;; there are errors in the split production. Return as conflict
                                (conflict {:status-text (format "The split production is not valid: \"%s\"" (string/join " " errors))
                                           :errors errors})
                                ;; all is well. Try to split the production
                                (try
                                  (prod/set-state-split! p volumes sample-rate bit-rate)
                                  (no-content)
                                  (catch clojure.lang.ExceptionInfo e
                                    (internal-server-error {:status-text (ex-message e)}))))))))}}]

    ["/:id/xml"
     {:get {:summary "Get the DTBook XML structure for a production"
            :parameters {:path {:id int?}}
            :handler (fn [{{{:keys [id]} :path} :parameters}]
                       (if-let [doc (prod/get-production id)]
                         (-> doc
                             dtbook/dtbook
                             ok
                             (content-type "text/xml")
                             (charset "UTF-8"))
                         (not-found)))}

      :post {:summary "Upload the DTBook XML structure for a production"
             :middleware [wrap-restricted wrap-authorized]
             :swagger {:security [{:apiAuth []}]}
             :authorized #{:etext :it}
             :parameters {:path {:id int?}
                          :multipart {:file multipart/temp-file-part}}
             :handler (fn [{{{:keys [file]} :multipart {:keys [id]} :path} :parameters}]
                        (let [tempfile (:tempfile file)
                              path (.getPath tempfile)
                              production (prod/get-production id)
                              errors (concat
                                      ;; validate XML
                                      (pipeline/validate path :dtbook)
                                      ;; validate meta data
                                      (validation/validate-metadata path production)
                                      ;; make sure production is in the state that allows upload
                                      (when (not (#{"new" "structured"} (:state production)))
                                        ["Production not in state \"new\" or \"structured\""]))]
                          (if (empty? errors)
                            (try
                              (prod/add-structure production tempfile)
                              (no-content)
                              (catch clojure.lang.ExceptionInfo e
                                (internal-server-error {:status-text (ex-message e)})))
                            (bad-request {:status-text "Upload of DTBook XML structure failed"
                                          :errors errors}))))}}]]

   ["/abacus"
    {:swagger {:tags ["Abacus API"]}}

    ["/new"
     {:post {:summary "Add a production"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (try
                          (let [tempfile (:tempfile file)
                                p (abacus/import-new-production tempfile)]
                            (created (str (:id p))))
                          (catch clojure.lang.ExceptionInfo e
                            (let [{:keys [error-id errors]} (ex-data e)]
                              (case error-id
                                :duplicate-key
                                (bad-request {:status-text (ex-message e)})
                                :invalid-xml
                                (bad-request {:status-text "Upload of ABACUS XML failed"
                                              :errors errors})
                                (internal-server-error {:status-text (ex-message e)}))))))}}]
    ["/recorded"
     {:post {:summary "Mark a production as recorded"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (let [tempfile (:tempfile file)]
                          (try
                            (abacus/import-recorded-production tempfile)
                            (no-content)
                            (catch clojure.lang.ExceptionInfo e
                              (let [{:keys [error-id errors]} (ex-data e)]
                                (case error-id
                                  :product-not-found (not-found)
                                  :invalid-state (bad-request {:status-text (ex-message e)})
                                  :invalid-daisy-export (bad-request {:status-text (ex-message e)})
                                  :invalid-xml (bad-request {:status-text "Upload of ABACUS XML failed" :errors errors})
                                  :invalid-exported-production (bad-request {:status-text (ex-message e) :errors errors})
                                  (internal-server-error {:status-text (ex-message e)})))))))}}]
    ["/status"
     {:post {:summary "Request the status of a production"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (let [tempfile (:tempfile file)]
                          (try
                            (abacus/import-status-request tempfile)
                            (no-content)
                            (catch clojure.lang.ExceptionInfo e
                              (let [{:keys [error-id errors]} (ex-data e)]
                                (case error-id
                                  :product-not-found (not-found)
                                  :invalid-xml (bad-request {:status-text "Upload of ABACUS XML failed" :errors errors})
                                  (internal-server-error {:status-text (ex-message e)})))))))}}]
    ["/metadata"
     {:post {:summary "Update the meta data of a production"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (try
                          (let [tempfile (:tempfile file)]
                            (abacus/import-metadata-update tempfile)
                            (no-content))
                          (catch clojure.lang.ExceptionInfo e
                            (let [{:keys [error-id errors]} (ex-data e)]
                              (case error-id
                                :invalid-xml (bad-request {:status-text "Upload of ABACUS XML failed"
                                                           :errors errors})
                                :product-not-found (not-found {:status-text (ex-message e)})
                                (internal-server-error))))))}}]]
   ["/vubis"
    {:swagger {:tags ["Upload of Vubis export data"]}}

    ["/upload"
     {:post {:summary "Upload XML from a Vubis export"
             :middleware [wrap-restricted wrap-authorized]
             :swagger {:security [{:apiAuth []}]}
             :authorized #{:etext :it}
             :parameters {:multipart {:file multipart/temp-file-part}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (let [tempfile (:tempfile file)
                              errors (vubis/validate (.getPath tempfile))]
                          (if (empty? errors)
                            (try
                              (let [productions (->> (vubis/read-file tempfile)
                                                     (map prod/add-default-meta-data))]
                                (ok productions))
                              (catch clojure.lang.ExceptionInfo e
                                (internal-server-error {:status-text (ex-message e)})))
                            (bad-request {:status-text "Not a valid Vubis export"
                                          :errors errors}))))}}]]])
