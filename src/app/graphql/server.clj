(ns app.graphql.server
  "Take a map of graphql queries and mutations and convert them into a lacina schema"
  (:require
   ;; require namespaces with resolvers
   [app.actor]
   [app.asset]
   [app.card]

   ;; deps
   [app.graphql.compiler :as gql.compiler]
   [app.graphql.resolvers :as gql.resolvers]
   [java-time.api :as t]
   [clojure.tools.logging :as log]
   [com.walmartlabs.lacinia :as lacina]
   [com.walmartlabs.lacinia.schema :as lacina.schema]
   [com.walmartlabs.lacinia.util :as lacina.util]
   [ring.util.response :as ring.response]
   [integrant.core :as ig]
   [app.authz.middleware :as authz]))

(defn- date-scalar
  [schema]
  (update-in schema [:scalars :Date] assoc
             :parse t/instant
             :serialize str))

(defn- uuid-scalar
  [schema]
  (update-in schema [:scalars :Uuid] assoc
             :parse parse-uuid
             :serialize str))

(defn- build-graphql-schema
  [resolvers-map]
  (clojure.pprint/pprint (gql.compiler/name->tuple->graphql-schema resolvers-map))
  (-> (gql.compiler/name->tuple->graphql-schema resolvers-map)
      date-scalar
      uuid-scalar
      (lacina.util/inject-resolvers (update-vals resolvers-map second))
      lacina.schema/compile))

(defn- with-middleware
  "Wrap graphql handlers with the provided list of middleware"
  [resolver-map & middleware]
  (update-vals resolver-map (fn [[schema resolver-fn]]
                              [schema
                               (reduce #(%2 %1) resolver-fn middleware)])))

(defn- wrap-graphql-request
  "Middleware that extracts graphql attributes from the request"
  [handler]
  (fn [{:keys [body] :as req}]
    (let [{:keys [query variables]} body]
      (log/debug {:query query :test "Test"})
      (handler (assoc req ::query query ::variables variables)))))

(defn handle-graphql
  "Handles a ring request with ::query and ::variables in the request objects"
  [schema {:keys [config]}]
  (fn [{:keys [app.graphql.server/query
               app.graphql.server/variables]
        :as req}]
    (-> (lacina/execute schema query variables {:request req :config config})
        ring.response/response
        (ring.response/status 200)
        (ring.response/content-type "application/json"))))

(defmethod ig/init-key ::handler [_ {:keys [resolvers] :as system}]
  (-> (build-graphql-schema resolvers)
      (handle-graphql system)
      wrap-graphql-request))

(defmethod ig/init-key ::resolvers [_ _]
  (merge (gql.resolvers/ns-gql-resolvers 'app.actor)
         (with-middleware (gql.resolvers/ns-gql-resolvers 'app.asset) authz/wrap-require-login)
         (with-middleware (gql.resolvers/ns-gql-resolvers 'app.card) authz/wrap-require-login)))
