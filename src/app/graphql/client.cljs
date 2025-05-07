(ns app.graphql.client
  (:require
   [app.graphql.transformer :as gql.transformer]
   [camel-snake-kebab.core :as csk]
   ["@apollo/client" :as apollo.client]))

(def client (apollo.client/ApolloClient. #js {:uri "/graphql"
                                              :cache (apollo.client/InMemoryCache.)}))

(defn use-query
  [query schema key & [variables]]
  (prn schema)
  (-> (apollo.client/gql query)
      (apollo.client/useQuery (clj->js {:variables (update-keys variables csk/->camelCase)}))
      (js->clj :keywordize-keys true)
      (update-in [:data key] gql.transformer/decode schema)))
