(ns app.graphql
  (:require [app.authn.middleware :refer [current-user]]
            [app.models.graphql-schema-adapter :as models.graphql]
            [com.walmartlabs.lacinia :refer [execute]]
            [com.walmartlabs.lacinia.util :refer [inject-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]
            [meta-merge.core :refer [meta-merge]]
            [app.models.core :as models]
            [ring.util.response :as ring.response]
            [tick.core :as t]))

(defn date-scalar
  [schema]
  (update-in schema [:scalars :Date] assoc
             :parse str
             :serialize t/instant))

(def resolvers
  {:Query/me [:models/User current-user]}
  ;;:Query/card [:models/Card (constantly {:cardType "blah"}) [:map [:card-name :string]]]
  ;;:Query/cards [:models/Card (constantly {:cardType "blah"})]
  )

(def graphql-types [:models/User :models/Card])

(defn- build-graphql-schema
  [types resolvers-map]
  (->
   (assoc-in
    (->> types
         (map models/schema)
         (map models.graphql/malli-schema->graphql-schema)
         (apply meta-merge))
    [:objects :Query :fields]
    (zipmap (->> resolvers-map keys (map (comp keyword name)))
            (for [[type _] (vals resolvers-map)]
              {:type (models.graphql/malli-schema->graphql-type-name type)})))
   date-scalar))

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
        (ring.response/content-type
         {:status 200
          :body (execute blood-bowl-schema query nil request)}
         "application/json")))))
