(ns app.authn.middleware
  (:require [app.dynamo :as ddb]
            [com.github.sikt-no.clj-jwt :as clj-jwt]))

(def jwks "https://www.googleapis.com/oauth2/v3/certs")

(defn make-authenticator [{:keys [dynamo]}]
  (fn [token]
    (try
      (if-let [sub (get (clj-jwt/unsign jwks token) :sub)]
        (let [user (ddb/get-item dynamo (str "user#" sub) "USER")]
          (if-not (empty? user)
            user
            (do
              (ddb/put-item dynamo (str "user#" sub) "USER" {"id" sub
                                                             "enrollment" {:S "incomplete"}
                                                             "username" {:S ""}})
              (ddb/get-item dynamo (str "user#" sub) "USER"))))
        {})
      (catch Throwable _ {}))))

(defn authenticate
  [authenticator handler]
  (fn [request]
    (handler (if-let [token (get-in request [:headers "authorization"])]
               (if-let [user (authenticator token)]
                 (assoc request :authenticated-user user)
                 request)
               request))))

(defn current-user
  [request]
  (:authenticated-user request))
