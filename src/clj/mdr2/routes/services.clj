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
    [mdr2.vubis :as vubis]
    [mdr2.production :as prod]
    [mdr2.production.spec :as prod.spec]
    [mdr2.repair.core :as repair]))

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
                             :or {limit default-limit offset 0}} :query} :parameters}]
                       (cond
                         (repair/production-id? search)
                         (ok (db/get-productions {:id (subs search 3) :state state
                                                  :limit limit :offset offset}))
                         (prod/library-signature? search)
                         (ok (db/get-productions {:library_signature search :state state
                                                  :limit limit :offset offset}))
                         (repair/product-number? search)
                         (ok (db/get-productions {:product_number search :state state
                                                  :limit limit :offset offset}))
                         :else
                         (ok (db/get-productions
                              {:limit limit :offset offset
                               :search (if (blank? search) nil (db/search-to-sql search))
                               :state state}))))}
      :put {:summary "Update or create a production"
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
                       (if-let [doc (db/get-production {:id id})]
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
                              (not-found))))}}]

    ["/:id/xml"
     {:get {:summary "Get DTBook XML for production"
            :parameters {:path {:id int?}}
            :handler (fn [{{{:keys [id]} :path} :parameters}]
                       (if-let [doc (db/get-production {:id id})]
                         (ok doc)
                         (not-found)))
            }}]]

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
                            (bad-request errors))))}}]]

   ["/files"
    {:swagger {:tags ["files"]}}

    ["/upload"
     {:post {:summary "upload a file"
             :parameters {:multipart {:file multipart/temp-file-part}}
             :responses {200 {:body {:name string?, :size int?}}}
             :handler (fn [{{{:keys [file]} :multipart} :parameters}]
                        {:status 200
                         :body {:name (:filename file)
                                :size (:size file)}})}}]

    ["/download"
     {:get {:summary "downloads a file"
            :swagger {:produces ["image/png"]}
            :handler (fn [_]
                       {:status 200
                        :headers {"Content-Type" "image/png"}
                        :body (-> "public/img/warning_clojure.png"
                                  (io/resource)
                                  (io/input-stream))})}}]]])
