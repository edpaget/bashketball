(ns app.authn.handler-test
  (:require
   [app.authn.handler :as authn.handler]
   [app.test-utils :as tu]
   [app.models :as models]
   [app.db :as db]
   [clojure.test :refer :all]
   [java-time.api :as t]))

;; --- Test Data ---

(def test-jwks-url "http://fake-jwks.com/certs")
(def test-strategy :identity-strategy/SIGN_IN_WITH_GOOGLE)
(def test-token "fake-jwt-token")
(def test-sub "user-subject-123")
(def existing-identity
  {:provider           test-strategy
   :provider-identity  test-sub})

;; --- Fixtures ---

(use-fixtures :once tu/db-fixture)
(use-fixtures :each tu/rollback-fixture)

;; --- Tests ---

(deftest make-id-token-authenticator-test

  (tu/with-global-frozen-time (t/with-offset (t/offset-date-time 2023 1 23 9 11) 0)
    (db/execute-one! {:insert-into [(models/->table-name ::models/Identity)]
                      :columns     (keys existing-identity)
                      :values      [(vals (update existing-identity :provider db/->pg_enum))]}))

  (testing "Successful authentication - existing identity"
    (tu/with-global-frozen-time (t/with-offset (t/offset-date-time 2023 1 23 11 11) 0)
      (let [authenticator (authn.handler/make-id-token-authenticator
                           {:jwks-url test-jwks-url
                            :strategy test-strategy}
                           (fn [jwks-url _]
                             (is (= test-jwks-url jwks-url))
                             {:sub test-sub}))
            result (authenticator {:token test-token})]
        (is (= (assoc existing-identity
                      :created-at #inst "2023-01-23T09:11:00.000-00:00"
                      :updated-at #inst "2023-01-23T11:11:00.000-00:00"
                      :last-successful-at #inst "2023-01-23T11:11:00.000-00:00"
                      :last-failed-at nil)
               result)
            "Should return the existing identity"))))
  (testing "Successful authentication - new identity"
    (tu/with-global-frozen-time (t/with-offset (t/offset-date-time 2023 1 23 9 11) 0)
      (let [authenticator (authn.handler/make-id-token-authenticator
                           {:jwks-url test-jwks-url
                            :strategy test-strategy}
                           (fn [jwks-url _]
                             (is (= test-jwks-url jwks-url))
                             {:sub "totally-different-sub"}))
            result (authenticator {:token test-token})]
        (is (= {:provider :identity-strategy/SIGN_IN_WITH_GOOGLE
                :provider-identity "totally-different-sub"
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
