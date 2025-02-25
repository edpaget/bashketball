(ns app.models.user
  (:require [app.models.core :as mc]))

(defmethod mc/schema :models/User [_]
  [:map {:pk [:id] :type "user"}
   [:id :string]
   [:enrollment-state :string]
   [:username [:maybe :string]]])
