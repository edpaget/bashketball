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
   ))

(def ^:private resolvers
  {:Query/me #'app.actor/current-actor})

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

(defn make-schema-handler
    [{:keys [resolvers-map] :or {resolvers-map resolvers}}]
    (let [schema (build-graphql-schema resolvers-map)]
      (fn [request]
        (let [{:strs [query variables]} (:body request)]
          (log/info query)
          (log/info variables)
          (ring.response/content-type
           {:status 200
            :body (lacina/execute schema
                                  query
                                  (update-keys variables keyword)
                                  {:request request})}
           "application/json")))))
