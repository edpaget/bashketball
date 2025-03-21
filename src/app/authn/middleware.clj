(ns app.authn.middleware
  (:require [app.models.core :as mc]
            [app.registry :refer [register-type!]]
            [ring.util.response :as ring.response]
            [malli.core :as m]
            [com.github.sikt-no.clj-jwt :as clj-jwt]))

(def jwks "https://www.googleapis.com/oauth2/v3/certs")
(def cookie-name "BB_COOKIE")

(register-type! :authenticator [:=> [:cat :str] [:maybe :models/User]])

(defn make-token-authenticator
  {:malli/schema [:=> [:cat [:map [:dynamo :any]]]
                  :authenticator]}
  [{:keys [dynamo]}]
  (fn [token]
    (when-let [sub (get (clj-jwt/unsign jwks token) :sub)]
      (let [user (mc/get-model dynamo :models/User {:id sub})]
        (if-not (empty? user)
          user
          (mc/save-model! dynamo :models/User {:id sub
                                               :enrollment-state "incomplete"
                                               :username ""}))))))

(defn make-session-authenticator
  [{:keys [dynamo]}]
  (fn [cookies]
    (when-let [session (mc/get-model dynamo
                                     :models/Session
                                     {:id (get-in cookies [cookie-name :value])})]
      (mc/get-model dynamo
                    :models/User
                    {:id (:user-id session)}))))

(defn wrap-session-authn
  [authorizer]
  (fn [handler]
    (fn [request]
      (if-let [user (authorizer (:cookies request))]
        (handler (assoc request :authenticated-user user))
        (handler request)))))

(defn create-session
  [{:keys [authenticator dynamo]}]
  (fn [{:keys [body cookies]}]

    (condp = (get body "action")
      "login" (if-let [user (authenticator (get body "id-token"))]
                (let [session-id (random-uuid)]
                  (if (mc/save-model! dynamo
                                      :models/Session
                                      {:id session-id
                                       :expires-at nil
                                       :user-id (:id user)})
                    (-> {:status 204}
                        (ring.response/set-cookie cookie-name session-id))
                    {:status 401}))
                {:status :401})
      "logout" (if-let [session-id (get cookies cookie-name)]
                 (do
                   (-> {:status 204}
                       (ring.response/set-cookie cookie-name session-id {:max-age 1})))
                 {:status 400})
      {:status 400})))

(m/=> current-user [:=> [:cat [:map [:request :map]] :any :any]
                    :models/User])
(defn current-user
  [{:keys [request]} _ _]
  (:authenticated-user request))
