(ns app.authn.middleware
  (:require [app.models.core :as mc]
            [com.github.sikt-no.clj-jwt :as clj-jwt]))

(def jwks "https://www.googleapis.com/oauth2/v3/certs")

(defn make-authenticator [{:keys [dynamo]}]
  (fn [token]
    (if-let [sub (get (clj-jwt/unsign jwks token) :sub)]
      (let [user (mc/get-model dynamo :models/User {:id sub})]
        (if-not (empty? user)
          user
          (mc/save-model! dynamo :models/User {:id sub
                                               :enrollment-state "incomplete"
                                               :username ""})))
      {})))

(defn authenticate
  [authenticator handler]
  (fn [request]
    (handler (if-let [token (get-in request [:headers "authorization"])]
               (if-let [user (authenticator token)]
                 (assoc request :authenticated-user user)
                 request)
               request))))

(defn current-user
  [request _ _]
  (:authenticated-user request))
