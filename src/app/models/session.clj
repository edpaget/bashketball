(ns app.models.session
  (:require [app.registry :refer [register-type!]]))

(register-type! :models/Session
                [:map {:pk [:id] :type "session"}
                 [:id :uuid]
                 [:user-id :string]
                 [:created-at {:optional true
                               :dynamo/on-create true
                               :default-now true} :time/instant]
                 [:expires-at [:maybe :time/instant]]])
