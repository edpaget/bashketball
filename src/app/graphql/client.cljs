(ns app.graphql.client
  (:require
   [clojure.walk :as walk]
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
   (let [[query* type-mappings] (gql.compiler/->query query query-name arguments)]
     (letfn [(decode-type-mapping [{:keys [__typename] :as result}]
               (if-let [schema (get type-mappings __typename)]
                 (gql.transformer/decode result schema)
                 result))
             (decode-type-mappings [data]
               (walk/postwalk (fn [x] (cond-> x
                                        (map? x) decode-type-mapping))
                              data))]
       (-> (apollo.client/gql query*)
           (apollo.client/useQuery (clj->js {:variables (update-keys variables csk/->camelCase)}))
           (js->clj :keywordize-keys true)
           (update :data decode-type-mappings))))))
