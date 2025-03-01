(ns app.graphql
  (:require [app.authn.middleware :refer [current-user]]
            [app.models.graphql-schema-adapter :as models.graphql]
            [com.walmartlabs.lacinia :refer [execute]]
            [com.walmartlabs.lacinia.util :refer [inject-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]
            [meta-merge.core :refer [meta-merge]]
            [app.models.core :as models]))

(def resolvers
  {:Query/me [:models/User current-user]})

(def graphql-types [:models/User :models/Card])

(defn- build-graphql-schema
  [types resolvers-map]
  (assoc-in
   (->> types
        (map models/schema)
        (map models.graphql/malli-schema->graphql-schema)
        (apply meta-merge))
   [:objects :Query :fields]
   (zipmap (->> resolvers-map keys (map (comp keyword name)))
           (->> resolvers-map vals
                (map (comp models.graphql/malli-schema->graphql-type-name first))
                (map #(assoc nil :type %))))))

(defn- injectable-resolvers
  [resolvers-map]
  (zipmap (keys resolvers-map) (->> resolvers-map vals (map second))))

(defn make-schema-handler
  []
  (let [blood-bowl-schema (-> (build-graphql-schema graphql-types resolvers)
                              (inject-resolvers (injectable-resolvers resolvers))
                              schema/compile)]
    (fn [request]
      (let [query (get-in request [:body "query"])]
        {:status 200
         :headers {"content-type" "application/json"}
         :body (execute blood-bowl-schema query nil request)}))))
