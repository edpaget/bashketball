(ns app.graphql
  (:require [app.authn :refer [current-user]]
            [clojure.edn :as edn]
            [com.walmartlabs.lacinia :refer [execute]]
            [com.walmartlabs.lacinia.util :refer [inject-resolvers]]
            [com.walmartlabs.lacinia.schema :as schema]))


(defn make-schema-handler
  []
  (let [blood-bowl-schema (-> "graphql/schema.edn"
                              slurp
                              edn/read-string
                              (inject-resolvers {:Query/me (fn [ctx _ _] (current-user ctx))})
                              schema/compile)]
    (fn [request]
      (let [query (get-in request [:body "query"])]
        {:status 200
         :headers {"content-type" "application/json"}
         :body (execute blood-bowl-schema query nil request)}))))
