(ns app.graphql.server
  "Take a map of graphql queries and mutations and convert them into a lacina schema"
  (:require
   [app.actor]
   [app.graphql.compiler :as gql.compiler]
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
  (-> (gql.compiler/name->var->graphql-schema resolvers-map)
      date-scalar
      (lacina.util/inject-resolvers (update-vals resolvers-map var-get))
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
  (let [schema (build-graphql-schema resolvers)]
    (wrap-graphql-request (handle-graphql schema))))

(defmethod ig/init-key ::resolvers [_ _]
  {:Query/me #'app.actor/current-actor})
