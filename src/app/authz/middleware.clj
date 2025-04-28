(ns app.authz.middleware
  (:require
   [app.db :as db]
   [app.models :as models]))

(defn- get-actor
  [app-authorization-id]
  (db/execute-one! {:select [:*]
                    :from   [[(models/->table-name ::models/Actor) :a]]
                    :join   [[(models/->table-name ::models/AppAuthorization) :aa] [:= :a.id :aa.actor_id]]
                    :where  [:= :aa.id app-authorization-id]}))

(defn wrap-current-actor
  "Ring middleware that gets the current actor from the session."
  [handler {:keys [cookie-name]}]
  (fn [{:keys [cookies] :as req}]
    (if-let [actor (get-actor (get cookies cookie-name))]
      (handler (assoc req :current-actor actor))
      (handler req))))
