(ns app.authn.handler
  (:require
   [app.db :as db]
   [app.models :as models]
   [app.registry :as registry]
   [com.github.sikt-no.clj-jwt :as clj-jwt]
   [java-time.api :as t]
   [malli.core :as m]
   [ring.util.response :as ring.response]
   [clojure.tools.logging :as log]))

(def jwks "https://www.googleapis.com/oauth2/v3/certs")
(def cookie-name "BB_COOKIE")

(registry/defschema :authenticator [:=> [:cat :map] [:maybe ::models/Identity]])

(defn make-id-token-authenticator
  {:malli/schema [:=> [:cat [:map
                             [:jwks-url :string]
                             [:strategy ::models/IdentityStrategy]]]
                  :authenticator]}
  ([opts]
   (make-id-token-authenticator opts clj-jwt/unsign))
  ([{:keys [jwks-url strategy]} unsign]
   (fn [{:keys [token]}]
     (try
       (when-let [sub (get (unsign jwks-url token) :sub)]
         (if (db/execute-one! {:select [true]
                               :from   [(models/->table-name ::models/Identity)]
                               :where  [:and
                                        [:= :provider #pg_enum strategy]
                                        [:= :provider-identity sub]]})
                                        ; update the models so we know when the last successful authentication was
           (db/execute-one! {:update    [(models/->table-name ::models/Identity)]
                             :set       {:last-successful-at (t/with-offset (t/offset-date-time) 0)}
                             :where     [:and
                                         [:= :provider #pg_enum strategy]
                                         [:= :provider-identity sub]]
                             :returning [:*]})

           (db/execute-one! {:insert-into [(models/->table-name ::models/Identity)]
                             :columns     [:provider :provider-identity :last-successful-at]
                             :values      [[#pg_enum strategy sub (t/with-offset (t/offset-date-time) 0)]]
                             :returning   [:*]})))
       (catch Throwable e
         (log/error e "failed to validate token")
         nil)))))
