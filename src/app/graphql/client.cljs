(ns app.graphql.client
  (:require
   [uix.core :as uix :refer [defhook]]
   [clojure.walk :as walk]
   [app.graphql.transformer :as gql.transformer]
   [app.graphql.compiler :as gql.compiler]
   [camel-snake-kebab.core :as csk]
   ["@apollo/client" :as apollo.client]))

(def client (apollo.client/ApolloClient. #js {:uri "/graphql"
                                              :cache (apollo.client/InMemoryCache.)}))

;; Helper Functions

(defn- transform-variable-keys
  "Transforms the keys of a variables map from kebab-case to camelCase."
  [variables-map]
  (walk/postwalk #(cond-> %1
                    (map? %1) (update-keys csk/->camelCase))
                 variables-map))

(defn- build-decode-response-data-fn
  "Returns a function that decodes response data based on type-mappings.
  The returned function takes the data (typically from a GraphQL response)
  and applies gql.transformer/decode to parts of it according to __typename."
  [type-mappings]
  (letfn [(decode-type-mapping [{:keys [__typename] :as result}]
            (if-let [schema (get type-mappings __typename)]
              (gql.transformer/decode result schema)
              result))]
    (fn [data]
      (walk/postwalk (fn [x] (cond-> x (map? x) decode-type-mapping)) data))))

(defn use-query
  ([query]
   (use-query query nil nil nil))
  ([query query-name]
   (use-query query query-name nil nil))
  ([query query-name arguments variables]
   (let [[query* type-mappings] (gql.compiler/->query query query-name arguments)
         decode-data-fn (build-decode-response-data-fn type-mappings)
         js-options (clj->js {:variables (transform-variable-keys variables)})]
     (-> (apollo.client/gql query*)
         (apollo.client/useQuery js-options)
         (js->clj :keywordize-keys true)
         (update :data decode-data-fn)))))

(defn- update-options
  [options]
  (clj->js
   (update options :variables transform-variable-keys)))

(defn use-mutation
  ([mutation-edn]
   (use-mutation mutation-edn nil nil nil))
  ([mutation-edn mutation-name]
   (use-mutation mutation-edn mutation-name nil nil))
  ([mutation-edn mutation-name mutation-args]
   (use-mutation mutation-edn mutation-name mutation-args nil))
  ([mutation-edn mutation-name mutation-args hook-options]
   (let [[mutation-string type-mappings] (gql.compiler/->mutation mutation-edn mutation-name mutation-args)
         gql-mutation (apollo.client/gql mutation-string)
         decode-data-fn (uix/use-callback (fn [& args] (apply (build-decode-response-data-fn type-mappings) args))
                                          [type-mappings])
         [original-mutate-fn mutation-state-js] (apollo.client/useMutation gql-mutation (update-options hook-options))]
     [(uix/use-callback
       (fn call-mutation
         ([] (call-mutation nil))
         ([execution-options]
          (.then (original-mutate-fn (update-options execution-options))
                 (fn [response]
                   (-> response
                       (js->clj :keywordize-keys true)
                       (update :data decode-data-fn))))))
       [original-mutate-fn decode-data-fn])
      (-> mutation-state-js
          (js->clj :keywordize-keys true)
          (update :data decode-data-fn))])))
