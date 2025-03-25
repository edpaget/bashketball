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
  {:Query/me [[:maybe :models/User] current-user]
   :Query/card [[:maybe :models/Card] (constantly (schema/tag-with-type {:name "test-card" :cardType "PLAYER"} :PlayerCard)) [:map [:card-name :string]]]
   :Query/cards [[:vector :models/Card] (constantly [(schema/tag-with-type {:name "test-card" :cardType "PLAYER"} :PlayerCard)])]})

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
            (for [[return _ & [args]] (vals resolvers-map)]
              (cond-> {:type (models.graphql/malli-schema->graph-returns return)}
                args (assoc :args (models.graphql/malli-schema->graph-args args))))))
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
      (let [{:strs [query variables]} (:body request)]
        (prn query)
        (prn variables)
        (ring.response/content-type
         {:status 200
          :body (execute blood-bowl-schema query (update-keys variables keyword) {:request request})}
         "application/json")))))
