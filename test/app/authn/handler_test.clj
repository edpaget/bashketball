(ns app.authn.handler-test
  (:require
   [app.authn.handler :as authn.handler]
   [app.test-utils :as tu]
   [app.models :as models]
   [app.db :as db]
   [clojure.test :refer :all]
   [java-time.api :as t]
   [camel-snake-kebab.core :as csk]))

;; --- Test Data ---

(def test-jwks-url "http://fake-jwks.com/certs")
(def test-strategy :identity-strategy/SIGN_IN_WITH_GOOGLE)
(def test-token "fake-jwt-token")
(def test-sub "user-subject-123")
(def existing-identity
  {:provider           test-strategy
   :provider-identity  test-sub
   :email              "test@example.com"})

;; --- Fixtures ---

(use-fixtures :once tu/db-fixture)
(use-fixtures :each tu/rollback-fixture)

;; --- Tests ---

(deftest make-id-token-authenticator-test
  (tu/with-global-frozen-time (t/with-offset (t/offset-date-time 2023 1 23 9 11) 0)
    (let [authenticator (authn.handler/make-id-token-authenticator
                         {:jwks-url test-jwks-url
                          :strategy test-strategy}
                         (fn [jwks-url _]
                           (is (= test-jwks-url jwks-url))
                           {:sub test-sub}))]

      (db/with-debug
        (db/execute-one! {:insert-into [(models/->table-name ::models/Identity)]
                          :columns     (map csk/->snake_case_keyword  (keys existing-identity))
                          :values      [(vals (update existing-identity :provider db/->pg_enum))]}))

      (testing "Successful authentication - existing identity"
        (let [result (authenticator {:token test-token})]
          (is (= (assoc existing-identity
                        :created-at #inst "2023-01-23T09:11:00.000-00:00"
                        :updated-at #inst "2023-01-23T09:11:00.000-00:00"
                        :last-successful-at #inst "2023-01-23T09:11:00.000-00:00"
                        :last-failed-at nil)
                 result)
              "Should return the existing identity")))
      (comment
        (testing "Successful authentication - new identity"
          ;; Make the SELECT mock return nil for this test case
          (let [mock-db-find-none-insert-new (fn [sql-map]
                                               (swap! captured-db-calls conj sql-map)
                                               (cond
                                                 (= :select (first (keys sql-map))) nil ; Simulate identity not found
                                                 (= :insert-into (first (keys sql-map))) new-identity
                                                 :else nil))]
            (with-redefs [clj-jwt/unsign mock-unsign
                          db/execute-one! mock-db-find-none-insert-new
                          t/instant mock-instant]
              (let [result (authenticator {:token test-token})]
                (is (= new-identity result) "Should return the newly created identity")

                (is (= 2 (count @captured-db-calls)) "Should have made two DB calls (SELECT, INSERT)")

                (let [[select-call insert-call] @captured-db-calls]
                  (is (= :select (first (keys select-call))) "First call is SELECT")

                  (is (= :insert-into (first (keys insert-call))) "Second call is INSERT")
                  (is (= [:provider :provider_identity :last_successful_at] (:columns insert-call)))
                  (is (= [[test-strategy test-sub fixed-instant]] (:values insert-call)))
                  (is (= [:*] (:returning insert-call))))))))

        (testing "Failed authentication - invalid token"
          (with-redefs [clj-jwt/unsign mock-unsign-fail
                        db/execute-one! mock-db-execute-one!] ; db shouldn't be called
            (let [result (authenticator {:token "invalid-token"})]
              (is (nil? result) "Should return nil on token validation failure")
              (is (empty? @captured-db-calls) "Should not make any DB calls"))))

        (testing "Failed authentication - nil token"
          (with-redefs [clj-jwt/unsign mock-unsign ; unsign shouldn't be called
                        db/execute-one! mock-db-execute-one!] ; db shouldn't be called
            (let [result (authenticator {:token nil})]
              (is (nil? result) "Should return nil if token is nil")
              (is (empty? @captured-db-calls) "Should not make any DB calls"))))))))
