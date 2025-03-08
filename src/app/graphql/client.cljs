(ns app.graphql.client
  (:require [uix.core :as uix :refer [defui $]]
            ["@apollo/client" :as apollo.client]))

(def client (apollo.client/ApolloClient. #js {:uri "/graphql"
                                              :cache (apollo.client/InMemoryCache.)}))

(defn use-query
  [query]
  (-> (apollo.client/gql query)
      apollo.client/useQuery
      (js->clj  :keywordize-keys true)))
