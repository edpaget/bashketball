(ns app.graphql.server
  "Take a map of graphql queries and mutations and convert them into a lacina schema"
  (:require
   [app.actor]
   [app.graphql.schema :as gql.schema]
   [java-time.api :as t]
   [clojure.tools.logging :as log]
   [malli.core :as mc]
   [com.walmartlabs.lacinia :refer [execute]]
   [com.walmartlabs.lacinia.util :refer [inject-resolvers]]))

(def resolvers
  {:Query/me #'app.actor/current-actor})

(defn date-scalar
  [schema]
  (update-in schema [:scalars :Date] assoc
             :parse str
             :serialize t/instant))

(defn build-graphql-schema
  [resolvers-map]
  (doseq [[k v] resolvers-map]
    (prn (-> v meta :schema ))))

(comment
  (defn make-schema-handler
    []
    (let [schema (build-graphql-schema resolvers)]
      (fn [request]
        (let [{:strs [query variables]} (:body request)]
          (log/info query)
          (log/info variables)
          (ring.response/content-type
           {:status 200
            :body (execute schema query (update-keys variables keyword) {:request request})}
           "application/json"))))))
