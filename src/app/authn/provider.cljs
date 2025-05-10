(ns app.authn.provider
  (:require
   [app.models :as models]
   [app.graphql.client :as gql.client]
   [uix.core :as uix :refer [defui $]]
   ;; npm
   ["@headlessui/react" :as headless]
   ["@react-oauth/google" :as gauth]))

(def client-id
  "964961527303-t0l0f6a8oa42p8c15928b4f4vavvbj9v.apps.googleusercontent.com")

(def auth-provider (uix/create-context {}))

(def ^:private get-me "query { me { id useName } }")

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
    ($ :div {:class "inline-flex items-center"}
       ($ gauth/GoogleLogin
          {:on-success #(set-token! (aget % "credential"))
           :on-error #(prn %)}))))

(defui logout-button []
  (let [{:keys [auth-status set-auth-status! set-token!]} (uix/use-context auth-provider)
        {:keys [data refetch]} (gql.client/use-query get-me ::models/Actor :me)]
    (uix/use-effect (fn [] (refetch)) [auth-status refetch])
    (when (-> data :me not-empty)
      ($ headless/Button {:type "button"
                          :class "inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md shadow-sm text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                          :on-click #(logout set-auth-status! set-token!)}
         "Logout"))))

(defui login-required [{:keys [show-prompt children]}]
  (let [{:keys [auth-status]} (uix/use-context auth-provider)
        {:keys [loading error data refetch]} (gql.client/use-query get-me ::models/Actor :me)]
    (uix/use-effect (fn [] (refetch)) [auth-status refetch])
    (cond
      loading ($ :p {:class "text-sm text-gray-500"} "Loading...")
      (not-empty error) ($ :p {:class "text-sm text-red-600"} "Something went wrong...")
      (not-empty (:me data)) children
      show-prompt ($ login-button)
      :else nil)))
