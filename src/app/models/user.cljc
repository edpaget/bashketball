(ns app.models.user
  (:require [app.registry :refer [register-type!]]))

(register-type! :models/User
                [:map {:pk [:id] :type "user"}
                 [:id :string]
                 [:enrollment-state :string]
                 [:username [:maybe :string]]])
