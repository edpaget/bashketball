(ns app.authz.middleware
  (:require
   [app.db :as db]
   [app.models :as models]
   [com.walmartlabs.lacinia.resolve :as resolve]
   [malli.experimental :as me]))

(me/defn get-actor! :- ::models/Actor
  [app-authorization-id :- :uuid]
  (db/execute-one! {:select [:a.*]
                    :from   [[(models/->table-name ::models/Actor) :a]]
                    :join   [[(models/->table-name ::models/AppAuthorization) :aa] [:= :a.id :aa.actor_id]]
                    :where  [:and [:= :aa.id app-authorization-id]
                             [:or [:= :aa.expires-at nil]
                              [:> :aa.expires-at :%now]]]}))

(defn wrap-current-actor
  "Ring middleware that gets the current actor from the session."
  [handler {:keys [cookie-name]}]
  (fn [{:keys [cookies] :as req}]
    (if-let [actor (some-> (get cookies cookie-name)
                           :value
                           parse-uuid
                           get-actor!)]
      (handler (assoc req :current-actor actor))
      (handler req))))

(defn wrap-require-login
  "Graphql middleware that asserts the current actor is present"
  [handler]
  (fn [{:keys [request] :as ctx} args parents]
    (if (:current-actor request)
      (handler ctx args parents)
      (resolve/resolve-as nil {:message "must be logged in"}))))
