(ns app.actor
  (:require
   [app.models :as models]
   [malli.experimental :as me]))

(me/defn current-actor :- [:maybe ::models/Actor]
  "Graphql query to "
  [_ :- :any
   _ :- :any
   {:keys [request]} :- [:map
                         [:request [:map
                                    [:current-actor ::models/Actor]]]]
   _ :- :any]
  (:current-actor request))
