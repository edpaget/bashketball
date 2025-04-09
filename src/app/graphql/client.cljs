(ns app.graphql.client
  (:require
   [app.models.core :as mc]
   [camel-snake-kebab.core :as csk]
   ["@apollo/client" :as apollo.client]))

(def client (apollo.client/ApolloClient. #js {:uri "/graphql"
                                              :cache (apollo.client/InMemoryCache.)}))

(defn use-query
  [query schema key & [variables]]
  (-> (apollo.client/gql query)
      (apollo.client/useQuery (clj->js {:variables (update-keys variables csk/->camelCase)}))
      (js->clj :keywordize-keys true)
      (update-in [:data key] #(do (prn "HERE" %) (when %
                                       (prn "VALUE" %)
                                       (try
                                         (mc/decode-graphql schema (dissoc % :__typename))
                                         (catch :default e
                                           (prn e))))))))
