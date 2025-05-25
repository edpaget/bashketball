(ns app.graphql.server
  "Take a map of graphql queries and mutations and convert them into a lacina schema"
  (:require
   ;; require namespaces with resolvers
   [app.actor]

   ;; deps
   [app.graphql.compiler :as gql.compiler]
   [app.graphql.resolvers :as gql.resolvers]
   [java-time.api :as t]
   [clojure.tools.logging :as log]
   [com.walmartlabs.lacinia :as lacina]
   [com.walmartlabs.lacinia.schema :as lacina.schema]
   [com.walmartlabs.lacinia.util :as lacina.util]
   [ring.util.response :as ring.response]
   [integrant.core :as ig]))

(defn- date-scalar
  [schema]
  (update-in schema [:scalars :Date] assoc
             :parse str
             :serialize t/instant))

(defn- build-graphql-schema
  [resolvers-map]
  (-> (gql.compiler/name->tuple->graphql-schema resolvers-map)
      date-scalar
      (lacina.util/inject-resolvers (update-vals resolvers-map second))
      lacina.schema/compile))

(defn- wrap-graphql-request
  "Middleware that extracts graphql attributes from the request"
  [handler]
  (fn [{:keys [body] :as req}]
    (let [{:keys [query variables]} body]
      (log/debug {:query query :variables variables :test "Test"})
      (handler (assoc req ::query query ::variables variables)))))

(defn handle-graphql
  "Handles a ring request with ::query and ::variables in the request objects"
  [schema]
  (fn [{:keys [app.graphql.server/query
               app.graphql.server/variables]
        :as req}]
    (-> (lacina/execute schema query variables {:request req})
        ring.response/response
        (ring.response/status 200)
        (ring.response/content-type "application/json"))))

(defmethod ig/init-key ::handler [_ {:keys [resolvers]}]
  (-> (build-graphql-schema resolvers)
      handle-graphql
      wrap-graphql-request))

(defmethod ig/init-key ::resolvers [_ _]
  @gql.resolvers/resolvers-registry)
