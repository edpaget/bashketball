(ns app.auth.provider
  (:require [uix.core :as uix :refer [defui $]]
            ["@react-oauth/google" :as gauth]
            ))

(def client-id
  "964961527303-t0l0f6a8oa42p8c15928b4f4vavvbj9v.apps.googleusercontent.com")


(def auth-provider (uix/create-context {}))

(defui authn [{:keys [children]}]
  (let [[oidc-token set-oidc-token!] (uix/use-state "")]
    ($ gauth/GoogleOAuthProvider {:client-id client-id}
       ($ auth-provider {:value {:auth-token oidc-token
                                 :set-token! set-oidc-token!}}
          children))))

(defui login-button []
  (let [{:keys [set-token!]} (uix/use-context auth-provider)]
    ($ gauth/GoogleLogin
       {:on-success #(set-token! (aget % "credential"))
        :on-error #(prn %)})))

(defui logout-button []
  (let [{:keys [set-token!] :as auth} (uix/use-context auth-provider)]
    (prn auth)
    ($ :button.logout {:type "button"
                       :on-click #(set-token! "")}
       "Logout")))

(defui login-required [{:keys [show-prompt children]}]
  (let [{:keys [auth-token]} (uix/use-context auth-provider)]
    (cond
      (not-empty auth-token) children
      show-prompt ($ login-button)
      :else nil)))
