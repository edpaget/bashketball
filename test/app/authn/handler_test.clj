(ns app.authn.handler-test
  (:require
   [app.authn.handler :as authn.handler]
   [app.test-utils :as tu]
   [app.models :as models]
   [app.db :as db]
   [clojure.test :refer [deftest is use-fixtures testing]]
   [java-time.api :as t]))

;; --- Test Data ---

(def test-jwks-url "http://fake-jwks.com/certs")
(def test-strategy :identity-strategy/SIGN_IN_WITH_GOOGLE)
(def test-token "fake-jwt-token")
(def test-email "bashketball@gmail.com")
(def existing-identity
  {:provider          test-strategy
   :provider-identity test-email
   :created-at        (t/with-offset (t/offset-date-time 2023 1 23 9 11) 0)
   :updated-at        (t/with-offset (t/offset-date-time 2023 1 23 9 11) 0)})
(def test-actor-id test-email) ; Actor ID matches provider identity

;; --- Fixtures ---

(use-fixtures :once tu/db-fixture)
(use-fixtures :each tu/rollback-fixture)

;; --- Mocks ---
(defn mock-authenticator-success
  "Mock authenticator that succeeds for test-token."
  [{:keys [token]}] ; Accepts the token directly as per make-token-authorization-creator usage
  (when (= token test-token)
    (db/execute-one! {:select [:*]
                      :from   [(models/->table-name ::models/Identity)]
                      :where  [:and [:= :provider #pg_enum test-strategy]
                               [:= :provider-identity test-email]]})))

(defn mock-authenticator-failure
  "Mock authenticator that always fails."
  [_] ; Accepts the token directly
  nil)

;; --- Mocks for make-authn-handler ---
(def test-session-id (java.util.UUID/randomUUID))
(def test-cookie-name "test-session")

(defn mock-auth-creator-success
  "Mock authorization creator that always succeeds."
  [_] ; Takes the request map
  [test-session-id 204])

(defn mock-auth-creator-failure
  "Mock authorization creator that always fails."
  [_] ; Takes the request map
  ["Auth failed!" 401])

;; --- Tests ---

(deftest make-id-token-authenticator-test
  (tu/with-inserted-data [::models/Identity (update existing-identity :provider db/->pg_enum)]
    (testing "Successful authentication - existing identity"
      (tu/with-global-frozen-time (t/with-offset (t/offset-date-time 2023 1 23 11 11) 0)
        (let [authenticator (authn.handler/make-id-token-authenticator
                             {:jwks-url test-jwks-url
                              :strategy test-strategy}
                             (fn [jwks-url _]
                               (is (= test-jwks-url jwks-url))
                               {:email test-email}))
              result (authenticator {:token test-token})]
          (is (= (assoc existing-identity
                        :created-at #inst "2023-01-23T09:11:00.000-00:00"
                        :updated-at #inst "2023-01-23T11:11:00.000-00:00"
                        :last-successful-at #inst "2023-01-23T11:11:00.000-00:00"
                        :last-failed-at nil)
                 result)
              "Should return the existing identity")))))
  (testing "Successful authentication - new identity"
    (tu/with-global-frozen-time (t/with-offset (t/offset-date-time 2023 1 23 9 11) 0)
      (let [authenticator (authn.handler/make-id-token-authenticator
                           {:jwks-url test-jwks-url
                            :strategy test-strategy}
                           (fn [jwks-url _]
                             (is (= test-jwks-url jwks-url))
                             {:email "totally-different-sub@gmail.com"}))
            result (authenticator {:token test-token})]
        (is (= {:provider :identity-strategy/SIGN_IN_WITH_GOOGLE
                :provider-identity "totally-different-sub@gmail.com"
                :created-at #inst "2023-01-23T09:11:00.000-00:00"
                :updated-at #inst "2023-01-23T09:11:00.000-00:00"
                :last-successful-at #inst "2023-01-23T09:11:00.000-00:00"
                :last-failed-at nil}
               result)
            "Should return the new identity"))))
  (testing "Failed authentication - invalid token"
    (let [authenticator (authn.handler/make-id-token-authenticator
                         {:jwks-url test-jwks-url
                          :strategy test-strategy}
                         (fn [_ _]
                           (throw (ex-info "failed to auth token" {}))))
          result (authenticator {:token "invalid-token"})]
      (is (nil? result) "Should return nil on token validation failure")))
  (testing "Failed authentication - nil token"
    (let [authenticator (authn.handler/make-id-token-authenticator
                         {:jwks-url test-jwks-url
                          :strategy test-strategy}
                         (fn [_ _]
                           (throw (ex-info "failed to auth token" {}))))
          result (authenticator {:token nil})]
      (is (nil? result) "Should return nil if token is nil"))))

(deftest make-token-authorization-creator-test-new-actor
  (let [frozen-time (t/with-offset (t/offset-date-time 2024 4 27 10 0 0) 0)]
    (tu/with-global-frozen-time frozen-time ; Use the correct macro
      (testing "Successful authorization"
        (tu/with-inserted-data [::models/Identity (update existing-identity :provider db/->pg_enum)]
          (let [creator (authn.handler/make-token-authorization-creator
                         {:authenticator mock-authenticator-success}) ; Pass the mock fn
                ;; Call creator with a single map argument
                [auth-id status] (creator {:token test-token :role "-self"})]
            (is (= 204 status))
            (is (uuid? auth-id))
            ;; Verify DB state (using kebab-case keys due to default builder)
            (let [actor (db/execute-one! {:select [:*]
                                          :from   [(models/->table-name ::models/Actor)]
                                          :where  [:= :id test-actor-id]})
                  auth (db/execute-one! {:select [:*]
                                         :from   [(models/->table-name ::models/AppAuthorization)]
                                         :where  [:= :id auth-id]})]
              (is (= {:id test-actor-id
                      :use-name nil
                      :enrollment-state "incomplete" ; Stored as string
                      :created-at #inst "2024-04-27T10:00:00.000-00:00"
                      :updated-at #inst "2024-04-27T10:00:00.000-00:00"}
                     actor) "Actor should be created")
              ;; Compare provider with its PGEnum representation from the DB
              (is (= {:id auth-id
                      :actor-id test-actor-id
                      :provider test-strategy
                      :provider-identity test-email
                      :created-at #inst "2024-04-27T10:00:00.000-00:00"
                      :expires-at nil}   ; expires_at is nullable
                     auth) "Authorization should be created"))))))))

(deftest make-token-authorization-creator-test-existing-actor
  (let [frozen-time (t/with-offset (t/offset-date-time 2024 4 27 10 0 0) 0)]
    (tu/with-global-frozen-time frozen-time ; Use the correct macro
      (tu/with-inserted-data [::models/Identity (update existing-identity :provider db/->pg_enum)]
        (testing "Successful authorization - existing actor"
          ;; Setup: Insert actor first with a different timestamp
          (let [initial-time (t/minus frozen-time (t/days 1))]
            (db/execute-one! {:insert-into [(models/->table-name ::models/Actor)]
                              :columns     [:id :enrollment-state :created-at :updated-at]
                              :values      [[test-actor-id "complete" initial-time initial-time]]}))
          (let [creator (authn.handler/make-token-authorization-creator
                         {:authenticator mock-authenticator-success})
                ;; Call creator with a single map argument
                [auth-id status] (creator {:token test-token :role "-self"})]
            (is (= 204 status))
            (is (uuid? auth-id))
            ;; Verify DB state (using kebab-case keys)
            (let [actor (db/execute-one! {:select [:*]
                                          :from   [(models/->table-name ::models/Actor)]
                                          :where  [:= :id test-actor-id]})
                  auth (db/execute-one! {:select [:*]
                                         :from   [(models/->table-name ::models/AppAuthorization)]
                                         :where  [:= :id auth-id]})]
              ;; Actor should not be updated (check timestamp)
              (is (=  #inst "2024-04-26T10:00:00.000-00:00" (:updated-at actor)) "Actor should not be updated")
              (is (= "complete" (:enrollment-state actor)) "Actor state should remain complete")
              (is (= {:id auth-id
                      :actor-id test-actor-id
                      :provider test-strategy
                      :provider-identity test-email
                      :created-at #inst "2024-04-27T10:00:00.000-00:00"
                      :expires-at nil}
                     auth) "New authorization should be created for existing actor"))))))))

(deftest make-token-authorization-creator-test-new-actor-failed
  (testing "Failed authentication - invalid token"
    (let [creator (authn.handler/make-token-authorization-creator
                   {:authenticator mock-authenticator-failure})
          ;; Call creator with a single map argument
          [message status] (creator {:token "invalid-token" :role "-self"})]
      (is (= 401 status))
      (is (= "Unable to authenticate token" message))))

  (testing "Failed authorization - non-self role"
    (tu/with-inserted-data [::models/Identity (update existing-identity :provider db/->pg_enum)]
      (let [creator (authn.handler/make-token-authorization-creator
                     {:authenticator mock-authenticator-success})
           ;; Call creator with a single map argument
            [message status] (creator {:token test-token :role "admin"})]
        (is (= 401 status))
        (is (= "Unable to assume role admin" message))))))


(deftest make-authn-handler-test
  (testing "Successful authentication"
    (let [handler (authn.handler/make-authn-handler
                   {:authorization-creator mock-auth-creator-success
                    :cookie-name           test-cookie-name})
          request {:body {:token "some-token" :action "login"}} ; Example request
          response (handler request)]
      (is (= 204 (:status response)))
      (is (= {test-cookie-name {:value test-session-id}}
             (get-in response [:cookies]))
          "Should set the session cookie")))

  (testing "Failed authentication"
    (let [handler (authn.handler/make-authn-handler
                   {:authorization-creator mock-auth-creator-failure
                    :cookie-name           test-cookie-name})
          request {:body {:token "bad-token" :action "login"}} ; Example request
          response (handler request)]
      (is (= 401 (:status response)))
      (is (= {:errors ["Auth failed!"]} (:body response)))
      (is (not (contains? (:headers response) "Set-Cookie"))
          "Should not set a cookie on failure")))

 (testing "Logout action"
   (let [logout-session-id (java.util.UUID/randomUUID)]
     ;; Ensure the actor exists for the foreign key constraint
     (tu/with-inserted-data [::models/Identity (update existing-identity :provider db/->pg_enum)
                             ::models/Actor {:id test-actor-id :enrollment-state "incomplete"}
                             ::models/AppAuthorization
                             {:id                logout-session-id
                              :actor-id          test-actor-id ; Assuming test-actor-id exists or is created elsewhere
                              :provider          #pg_enum test-strategy
                              :provider-identity test-email
                              :created-at        (t/instant)}]
       (let [handler (authn.handler/make-authn-handler
                      {:authorization-creator mock-auth-creator-success ; Not called for logout
                       :cookie-name           test-cookie-name})
             request {:cookies {test-cookie-name logout-session-id} ; Add cookie header
                      :body    {:action "logout"}} ; Logout request
             response (handler request)]
         (is (= 204 (:status response)))
         (is (= {test-cookie-name {:value logout-session-id :max-age 1}} ; Correct max-age for expired cookie
                (:cookies response)) ; Use :cookies directly as ring sets it this way
             "Should set an expired session cookie"))))))
