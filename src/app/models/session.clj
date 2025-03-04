(ns app.models.session
  (:require [app.registry :refer [register-type!]]))

(register-type! :models/Session
                [:map {:pk [:id] :type "session"}
                 [:id :uuid]
                 [:user-id :string]
                 [:created-at {:optional true} [:maybe :time/zoned-date-time]]
                 [:expires-at {:optional true} [:maybe :time/zoned-date-time]]])
