(ns app.graphql.client
  (:require
   [camel-snake-kebab.core :as csk]
   ["@apollo/client" :as apollo.client]))

(def client (apollo.client/ApolloClient. #js {:uri "/graphql"
                                              :cache (apollo.client/InMemoryCache.)}))

(defn use-query
  [query & [variables]]
  (-> (apollo.client/gql query )
      (apollo.client/useQuery (clj->js {:variables (update-keys variables csk/->camelCase)}))
      (js->clj :keywordize-keys true)))
