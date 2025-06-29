(ns app.actor.resolvers
  (:require
   [app.graphql.resolvers :as gql]
   [app.models :as models]))

(gql/defresolver :Query/me
  "Graphql query to get the current actor of a request"
  [:=> [:cat [:map
              [:request [:map
                         [:current-actor {:optional true} ::models/Actor]]]]
        :any
        :any]
   [:maybe ::models/Actor]]
  [{:keys [request]} _ _]
  (:current-actor request))
