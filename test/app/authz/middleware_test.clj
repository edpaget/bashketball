(ns app.authz.middleware-test
  (:require
   [app.authz.middleware :refer [wrap-current-actor]]
   [app.db]
   [app.models :as models]
   [app.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]))

(use-fixtures :once tu/db-fixture)
(use-fixtures :each tu/rollback-fixture)

(def ^:private test-cookie-name "test-session-id")
(def ^:private test-authz-id #uuid "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11") ; Use a real UUID
(def ^:private test-actor-id "test-actor-id@example.com") ; Use a real email
(def ^:private test-actor-data {:id test-actor-id :use-name "Test Actor" :enrollment-state "complete"}) ; Match DB columns
(def ^:private test-identity-data {:provider #pg_enum :identity-strategy/INVALID :provider-identity test-actor-id}) ; Match DB columns
(def ^:private test-authz-data (merge {:id test-authz-id :actor-id test-actor-id} test-identity-data)) ; Match DB columns

(deftest wrap-current-actor-test
  (testing "when cookie exists and actor is found"
    (tu/with-inserted-data [::models/Actor test-actor-data
                            ::models/Identity test-identity-data
                            ::models/AppAuthorization test-authz-data]
      (let [request {:cookies {test-cookie-name (str test-authz-id)}} ; Cookie value must be string
            handler-called? (atom false)
            handler (fn [req]
                      (reset! handler-called? true)
                      ;; Check that the actor map from DB (with created_at/updated_at) is attached
                      (is (= test-actor-id (-> req :current-actor :id)))
                      (is (= (:use-name test-actor-data) (-> req :current-actor :use-name)))
                      (is (some? (-> req :current-actor :created-at))) ; Check timestamps exist
                      (is (some? (-> req :current-actor :updated-at)))
                      (is (= (dissoc req :current-actor) request) "Original request keys should be preserved")
                      {:status 200})
            wrapped-handler (wrap-current-actor handler {:cookie-name test-cookie-name})]
        (wrapped-handler request)
        (is @handler-called? "Handler should be called"))))

  (testing "when cookie exists but actor is not found (no matching AppAuthorization)"
    (let [request {:cookies {test-cookie-name (str test-authz-id)}} ; Use the ID, but don't insert it
          handler-called? (atom false)
          handler (fn [req]
                    (reset! handler-called? true)
                    (is (= request req) "Request should not have :current-actor")
                    {:status 200})
          wrapped-handler (wrap-current-actor handler {:cookie-name test-cookie-name})]
      (wrapped-handler request)
      (is @handler-called? "Handler should be called")))

  (testing "when cookie exists but actor is not found "
    (let [request {:cookies {test-cookie-name (str test-authz-id)}}
          handler-called? (atom false)
          handler (fn [req]
                    (reset! handler-called? true)
                    (is (= request req) "Request should not have :current-actor")
                    {:status 200})
          wrapped-handler (wrap-current-actor handler {:cookie-name test-cookie-name})]
      (wrapped-handler request)
      (is @handler-called? "Handler should be called")))


  (testing "when cookie does not exist"
    (let [request {:cookies {"other-cookie" "other-value"}}
          handler-called? (atom false)
          handler (fn [req]
                    (reset! handler-called? true)
                    (is (= request req) "Request should be unchanged")
                    {:status 200})
          wrapped-handler (wrap-current-actor handler {:cookie-name test-cookie-name})]
      (wrapped-handler request)
      (is @handler-called? "Handler should be called")))

  (testing "when cookies key is missing in request"
    (let [request {:uri "/"} ; No :cookies key
          handler-called? (atom false)
          handler (fn [req]
                    (reset! handler-called? true)
                    (is (= request req) "Request should be unchanged")
                    {:status 200})
          wrapped-handler (wrap-current-actor handler {:cookie-name test-cookie-name})]
      (wrapped-handler request)
      (is @handler-called? "Handler should be called"))))
