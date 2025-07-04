(ns app.authn.provider
  (:require
   ;; npm
   ["@headlessui/react" :as headless]
   ["@react-oauth/google" :as gauth]

   [app.graphql.client :as gql.client]
   [app.models :as models]
   [uix.core :as uix :refer [defui $]]))

(def client-id
  "964961527303-t0l0f6a8oa42p8c15928b4f4vavvbj9v.apps.googleusercontent.com")

(def ^:private auth-provider (uix/create-context {}))

(def ^:private get-me {:Query/me [::models/Actor :id]})

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
        {:keys [data refetch]} (gql.client/use-query get-me)
        [auth-status set-auth-status!] (uix/use-state :logged-out)]
    (uix/use-effect (fn [] (refetch)) [auth-status refetch])
    (uix/use-effect (fn [] (set-auth-status! (if (-> data :me :id) :logged-in :logged-out)))
                    [data])
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
  (let [{:keys [auth-status set-auth-status! set-token!]} (uix/use-context auth-provider)]
    (when (= auth-status :logged-in)
      ($ headless/Button {:type "button"
                          :class "inline-flex items-center px-4 py-2 border border-gray-300 text-sm font-medium rounded-md shadow-sm text-gray-700 bg-white hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500"
                          :on-click #(logout set-auth-status! set-token!)}
         "Logout"))))

(defui login-required [{:keys [show-prompt children]}]
  (let [{:keys [loading error data]} (gql.client/use-query get-me)]
    (cond
      loading ($ :p {:class "text-sm text-gray-500"} "Loading...")
      (not-empty error) ($ :p {:class "text-sm text-red-600"} "Something went wrong...")
      (not-empty (:me data)) children
      show-prompt ($ login-button)
      :else nil)))
