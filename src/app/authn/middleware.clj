(ns app.authn.middleware
  (:require
   [app.db :as db]
   [app.models :as models]
   [app.registry :as registry]
   [com.github.sikt-no.clj-jwt :as clj-jwt]
   [java.time :as t]
   [malli.core :as m]
   [ring.util.response :as ring.response]))

(def jwks "https://www.googleapis.com/oauth2/v3/certs")
(def cookie-name "BB_COOKIE")

(registry/defschema :authenticator [:=> [:cat :map] [:maybe :models/Identity]])

(defn make-id-token-authenticator
  {:malli/schema [:=> [:cat [:map
                             [:jwks-url :string]
                             [:strategy :models/IdentityStrategy]]]
                  :authenticator]}
  [{:keys [jwks-url strategy]}]
  (fn [{:keys [token]}]
    (when-let [sub (get (clj-jwt/unsign jwks-url token) :sub)]
      (if-let [identity (db/execute-one! {:select [:*]
                                          :from [(models/->table-name :models/Identity)]
                                          :where [:and
                                                  [:= :provider strategy]
                                                  [:= :provider-identity sub]]})]
        (do
          ; update the models so we know when the last successful authentication was
          (db/execute-one! {:update [(models/->table-name :models/Identity)]
                            :set    {:last_successful_at (t/instant)}})
          ;; return identity
          identity)
        (db/execute-one! {:insert-into [(models/->table-name :models/Identity)]
                          :columns     [:provider :provider_identity :last_successful_at]
                          :values      [[strategy sub (t/instant)]]
                          :returning   [:*]})))))

;; (defn create-session
;;   [{:keys [authenticator dynamo]}]
;;   (fn [{:keys [body cookies]}]

;;     (condp = (get body "action")
;;       "login" (if-let [user (authenticator (get body "id-token"))]
;;                 (let [session-id (random-uuid)]
;;                   (if (mc/save-model! dynamo
;;                                       :models/Session
;;                                       {:id session-id
;;                                        :expires-at nil
;;                                        :user-id (:id user)})
;;                     (-> {:status 204}
;;                         (ring.response/set-cookie cookie-name session-id))
;;                     {:status 401}))
;;                 {:status :401})
;;       "logout" (if-let [session-id (get cookies cookie-name)]
;;                  (do
;;                    (-> {:status 204}
;;                        (ring.response/set-cookie cookie-name session-id {:max-age 1})))
;;                  {:status 400})
;;       {:status 400})))

;; (m/=> current-user [:=> [:cat [:map [:request :map]] :any :any]
;;                     :models/User])
;; (defn current-user
;;   [{:keys [request]} _ _]
;;   (:authenticated-user request))
