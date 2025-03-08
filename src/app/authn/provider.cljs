(ns app.authn.provider
  (:require [uix.core :as uix :refer [defui $]]
            [app.graphql.client :as graphql.client]
            ["@react-oauth/google" :as gauth]))

(def client-id
  "964961527303-t0l0f6a8oa42p8c15928b4f4vavvbj9v.apps.googleusercontent.com")

(def auth-provider (uix/create-context {}))

(defn login
  [oidc-token set-auth-status!]
  (when-not (empty? oidc-token)
    (..
     (js/fetch "/authn"
               #js {"method" "POST"
                    "body" (.stringify js/JSON
                                       #js {"id-token" oidc-token
                                            "action" "login"})
                    "headers" #js {"content-type" "application/json"}})
     (then #(set-auth-status! :logged-in)))))

(defn logout
  [set-auth-status! set-token!]
  (.. (js/fetch "/authn"
                #js {"method" "POST"
                     "body" (.stringify js/JSON
                                        #js {"action" "logout"})
                     "headers" #js {"content-type" "application/json"}})
      (then #(set-auth-status! :logged-out))
      (then #(set-token! ""))))

(defui authn [{:keys [children]}]
  (let [[oidc-token set-oidc-token!] (uix/use-state "")
        [auth-status set-auth-status!] (uix/use-state :logged-out)]
    (uix/use-effect (fn [] (login oidc-token set-auth-status!)) [oidc-token])
    ($ gauth/GoogleOAuthProvider {:client-id client-id}
       ($ auth-provider {:value {:id-token oidc-token
                                 :auth-status auth-status
                                 :set-auth-status! set-auth-status!
                                 :set-token! set-oidc-token!}}
          children))))

(defui login-button []
  (let [{:keys [set-token!]} (uix/use-context auth-provider)]
    ($ gauth/GoogleLogin
       {:on-success #(set-token! (aget % "credential"))
        :on-error #(prn %)})))

(defui logout-button []
  (let [{:keys [auth-status set-auth-status! set-token!]} (uix/use-context auth-provider)
        {:keys [data refetch]} (graphql.client/use-query  "query { me { id username } }")]
    (uix/use-effect (fn [] (refetch)) [auth-status refetch])
    (when (-> data :me not-empty)
      ($ :button.logout {:type "button"
                         :on-click #(logout set-auth-status! set-token!)}
         "Logout"))))

(defui login-required [{:keys [show-prompt children]}]
  (let [{:keys [auth-status]} (uix/use-context auth-provider)
        {:keys [loading error data refetch]} (graphql.client/use-query
                                              "query { me { id username } }")]
    (uix/use-effect (fn [] (refetch)) [auth-status refetch])
    (when error
      (prn error))
    (cond
      loading ($ :p "Loading...")
      (not-empty error) ($ :p "Something went wrong...")
      (not-empty (:me data)) children
      show-prompt ($ login-button)
      :else nil)))
