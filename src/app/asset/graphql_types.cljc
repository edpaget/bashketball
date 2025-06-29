(ns app.asset.graphql-types
  (:require
   [app.registry :as registry]))

(registry/defschema ::create-asset-args
  [:map
   [:mime-type :string]
   [:img-blob :string]])
