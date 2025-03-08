(ns app.authn.provider
  (:require [uix.core :as uix :refer [defui $]]
            [app.graphql.client :as graphql.client]
            ["@react-oauth/google" :as gauth]))

(def client-id
  "964961527303-t0l0f6a8oa42p8c15928b4f4vavvbj9v.apps.googleusercontent.com")

(def auth-provider (uix/create-context {}))

(defui authn [{:keys [children]}]
  (let [[oidc-token set-oidc-token!] (uix/use-state "")]
    (uix/use-effect (fn []
                      (when-not (empty? oidc-token)
                        (js/fetch "/authn"
                                  #js {"method" "POST"
                                       "body" (.stringify js/JSON
                                                          #js {"id-token" oidc-token})
                                       "headers" #js {"content-type" "application/json"}})))
                    [oidc-token])
    (prn oidc-token)
    ($ gauth/GoogleOAuthProvider {:client-id client-id}
       ($ auth-provider {:value {:id-token oidc-token
                                 :set-token! set-oidc-token!}}
          children))))

(defui login-button []
  (let [{:keys [set-token!]} (uix/use-context auth-provider)]
    ($ gauth/GoogleLogin
       {:on-success #(set-token! (aget % "credential"))
        :on-error #(prn %)})))

(defui logout-button []
  (let [{:keys [loading error data]} (graphql.client/use-query  "query { me { id username } }")]
    (prn loading)
    (prn error)
    (prn data)
    (when data
      ($ :button.logout {:type "button"
                         :on-click #(prn "HERE")}
         "Logout"))))

(defui login-required [{:keys [show-prompt children]}]
  (let [{:keys [loading error data]} (graphql.client/use-query  "query { me { id username } }")]
    (prn "DATA" data)
    (cond
      (not-empty data) children
      show-prompt ($ login-button)
      :else nil)))
