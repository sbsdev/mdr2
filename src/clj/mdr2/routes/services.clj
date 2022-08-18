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
    [mdr2.middleware.formats :as formats]
    [ring.util.http-response :refer :all]
    [clojure.string :refer [blank?]]
    [clojure.java.io :as io]
    [de.otto.nom.core :as nom]
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
    :swagger {:id ::api}
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
             ;;:middleware [wrap-restricted wrap-authorized]
             ;;:swagger {:security [{:apiAuth []}]}
             :parameters {:body prod.spec/production}
             :handler (fn [{{p :body} :parameters}]
                        (let [p (prod/create! p)]
                          (if-not (nom/anomaly? p)
                            (no-content)
                            (bad-request {:status-text
                                          (ex-message (:exception (nom/payload p)))}))))}}]

    ["/:id"
     {:get {:summary "Get a production by ID"
            :parameters {:path {:id int?}}
            :handler (fn [{{{:keys [id]} :path} :parameters}]
                       (if-let [doc (prod/get-production id)]
                         (ok doc)
                         (not-found)))}

      :delete {:summary "Delete a production"
               ;;:middleware [wrap-restricted wrap-authorized]
               ;;:swagger {:security [{:apiAuth []}]}
               :parameters {:path {:id int?}}
               :handler (fn [{{{:keys [id]} :path} :parameters}]
                          (let [deleted (db/delete-production {:id id})]
                            (if (>= deleted 1)
                              (no-content)
                              (not-found))))}

      :patch {:summary "Patch a production, e.g. update the library_signature"
              ;;:middleware [wrap-restricted wrap-authorized]
              ;;:swagger {:security [{:apiAuth []}]}
              :parameters {:path {:id int?}
                           :body {:library_signature ::prod.spec/library_signature}}
              :handler (fn [{{{:keys [id]} :path {:keys [library_signature]} :body} :parameters}]
                         (let [p (prod/get-production id)]
                           (cond
                             (nil? p) (not-found)
                             (not= (:state p) "encoded") (conflict {:status-text "Only encoded productions can be assigned a library signature"})
                             :else (let [p (-> p
                                            (assoc :library_signature library_signature)
                                            prod/set-state-cataloged!)]
                                     (if-not (nom/anomaly? p)
                                       (no-content)
                                       (bad-request {:status-text
                                                     (ex-message (:exception (nom/payload p)))}))))))}}]


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
                          (if-not (seq errors)
                            (let [p (prod/add-structure production tempfile)]
                              (if-not (nom/anomaly? p)
                                (no-content)
                                (internal-server-error)))
                            (bad-request {:status-text "Upload of DTBook XML structure failed"
                                          :errors errors}))))}}]]

   ["/abacus"
    {:swagger {:tags ["Abacus API"]}}

    ["/new"
     {:post {:summary "Add a production"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (let [tempfile (:tempfile file)
                              p (abacus/import-new-production tempfile)]
                          (if-not (nom/anomaly? p)
                            (created)
                            (bad-request p))))}}]
    ["/recorded"
     {:post {:summary "Mark a production as recorded"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (let [tempfile (:tempfile file)
                              p (abacus/import-recorded-production tempfile)]
                          (if-not (nom/anomaly? p)
                            (no-content)
                            (bad-request p))))}}]
    ["/status"
     {:post {:summary "Request the status of a production"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (let [tempfile (:tempfile file)
                              p (abacus/import-status-request tempfile)]
                          (if-not (nom/anomaly? p)
                            (no-content)
                            (bad-request p))))}}]
    ["/metadata"
     {:post {:summary "Update the meta data of a production"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (let [tempfile (:tempfile file)
                              p (abacus/import-metadata-update tempfile)]
                          (if-not (nom/anomaly? p)
                            (no-content)
                            (bad-request p))))}}]]
   ["/vubis"
    {:swagger {:tags ["Upload of Vubis export data"]}}

    ["/upload"
     {:post {:summary "Upload XML from a Vubis export"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        (let [tempfile (:tempfile file)
                              errors (vubis/validate (.getPath tempfile))]
                          (if (empty? errors)
                            (let [productions (->> (vubis/read-file tempfile)
                                                   (map prod/add-default-meta-data))]
                              (if-not (nom/anomaly? productions)
                                (ok productions)
                                (bad-request productions)))
                            (bad-request {:status-text "Not a valid Vubis export"
                                          :errors errors}))))}}]]])
