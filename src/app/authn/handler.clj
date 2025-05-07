(ns app.authn.handler
  (:require
   [app.db :as db]
   [app.models :as models]
   [app.registry :as registry]
   [com.github.sikt-no.clj-jwt :as clj-jwt]
   [integrant.core :as ig]
   [java-time.api :as t]
   [malli.experimental :as me]
   [ring.util.response :as ring.response]
   [clojure.tools.logging :as log]
   [clojure.string :as str]))

(registry/defschema ::authenticator
  [:=> [:cat :map] [:maybe ::models/Identity]])

(registry/defschema ::jwt-unsign
  [:=> [:cat :string] [:map [:email :string]]])

(me/defn make-id-token-authenticator :- ::authenticator
  ([opts :- [:map
             [:jwks-url :string]
             [:strategy ::models/IdentityStrategy]]]
   (make-id-token-authenticator opts clj-jwt/unsign))
  ([{:keys [jwks-url strategy]} :- [:map
                                    [:jwks-url :string]
                                    [:strategy ::models/IdentityStrategy]]
    unsign :- ::jwt-unsign]
   (fn [{:keys [token]}]
     (try
       (when-let [email (get (unsign jwks-url token) :email)]
         (when (str/ends-with? email "@gmail.com")
           (if (db/execute-one! {:select [true]
                                 :from   [(models/->table-name ::models/Identity)]
                                 :where  [:and
                                          [:= :provider #pg_enum strategy]
                                          [:= :provider-identity email]]})
                                        ; update the models so we know when the last successful authentication was
             (db/execute-one! {:update    [(models/->table-name ::models/Identity)]
                               :set       {:last-successful-at (t/with-offset (t/offset-date-time) 0)}
                               :where     [:and
                                           [:= :provider #pg_enum strategy]
                                           [:= :provider-identity email]]
                               :returning [:*]})

             (db/execute-one! {:insert-into [(models/->table-name ::models/Identity)]
                               :columns     [:provider :provider-identity :last-successful-at]
                               :values      [[#pg_enum strategy email (t/with-offset (t/offset-date-time) 0)]]
                               :returning   [:*]}))))
       (catch Throwable e
         (log/error e "failed to validate token")
         nil)))))

(registry/defschema ::authorization-creator
  [:=> [:cat :map]
   [:or [:tuple :uuid [:= 204]]
    [:tuple :string [:enum 401]]]])

(me/defn make-token-authorization-creator :- ::authorization-creator
  [{:keys [authenticator]}]
  (fn [{:keys [token role]}]
    (let [identity (authenticator {:token token})]
      (cond
        (nil? identity)
        ["Unable to authenticate token" 401]

        ;; not allowed in ask for non-self roles yet
        (not= "-self" role)
        [(format "Unable to assume role %s" role) 401]

        :else
        (let [{:keys [provider provider-identity]} identity]
          (db/with-transaction [_conn]
            (when-not (db/execute-one! {:select [true]
                                        :from [(models/->table-name ::models/Actor)]
                                        :where [:= :id provider-identity]})
              (db/execute-one! {:insert-into [(models/->table-name ::models/Actor)]
                                :columns     [:id :enrollment-state]
                                :values      [[provider-identity "incomplete"]]}))
            [(:id
              (db/execute-one! {:insert-into [(models/->table-name ::models/AppAuthorization)]
                                :columns     [:actor_id :provider :provider-identity]
                                :values      [[provider-identity #pg_enum provider provider-identity]]
                                :returning   [:id]}))
             204]))))))

(me/defn make-authn-handler :- [:map [:status :int] [:cookies :map] [:body :map]]
  "Ring handler for the authn-endpoint"
  [{:keys [authorization-creator cookie-name]} :- [:map
                                                   [:authorization-creator ::authorization-creator]
                                                   [:cookie-name :string]]]
  (fn [{:keys [body cookies]}]
    (condp = (get body :action)
      "login" (let [[result status] (authorization-creator {:token (get body :id-token)
                                                            :role (get body :role "-self")})]
                (condp = status
                  204 (-> {:status status}
                          (ring.response/set-cookie cookie-name result))
                  401 {:status status
                       :body {:errors [result]}}))
      "logout" (if-let [session-id (some-> (get cookies cookie-name)
                                           :value
                                           parse-uuid)]
                 (do
                   (db/execute-one! {:update [(models/->table-name ::models/AppAuthorization)]
                                     :set    {:expires-at (t/with-offset (t/offset-date-time) 0)}
                                     :where  [:= :id session-id]})
                   (-> {:status 204}
                       (ring.response/set-cookie cookie-name session-id {:max-age 1})))
                 {:status 400
                  :body {:errors ["No session to logout"]}}))))

(defmethod ig/init-key ::auth-handler [_ {:keys [config]}]
  (let [{:keys [cookie-name google-jwks]} (:auth config)]
    (make-authn-handler
     {:cookie-name cookie-name
      :authorization-creator (make-token-authorization-creator
                              {:authenticator (make-id-token-authenticator
                                               {:jwks-url google-jwks
                                                :strategy :identity-strategy/SIGN_IN_WITH_GOOGLE})})})))
