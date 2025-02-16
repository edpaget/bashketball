(ns app.models.user
  (:require [malli.core :as m])
  )

(def User
  [:map
   [:id :string]
   [:enrollment-state :string]
   [:username [:maybe :string]]])

