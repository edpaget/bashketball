(ns app.asset.graphql-types
  #?(:cljs
     (:require-macros
      [app.registry :as registry]))
  (:require
   [app.registry :as registry]))

(registry/defschema ::create-asset-args
  [:map
   [:mime-type :string]
   [:img-blob :string]])
