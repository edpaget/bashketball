(ns app.graphql.client
  (:require
   [app.graphql.transformer :as gql.transformer]
   [app.graphql.compiler :as gql.compiler]
   [camel-snake-kebab.core :as csk]
   ["@apollo/client" :as apollo.client]))

(def client (apollo.client/ApolloClient. #js {:uri "/graphql"
                                              :cache (apollo.client/InMemoryCache.)}))

(defn use-query
  ([query]
   (use-query query nil nil nil))
  ([query query-name]
   (use-query query query-name nil nil))
  ([query query-name arguments variables]
   (let [keys (map name (keys query))
         [query* type-mappings] (gql.compiler/->query query query-name arguments)]
     (prn query*)
     (letfn [(decode-type-mapping [{:keys [__typename] :as result}]
               (if-let [schema (get type-mappings __typename)]
                 (gql.transformer/decode schema result)
                 result))
             (decode-type-mappings [data]
               (reduce (fn [accum key]
                         (update accum key decode-type-mapping))
                       data keys))]
       (-> (apollo.client/gql query*)
           (apollo.client/useQuery (clj->js {:variables (update-keys variables csk/->camelCase)}))
           (js->clj :keywordize-keys true)
           (update :data decode-type-mappings))))))
