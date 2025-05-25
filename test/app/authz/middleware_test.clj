(ns app.authz.middleware-test
  (:require
   [app.authz.middleware :refer [wrap-current-actor get-actor!]]
   [app.db]
   [app.models :as models]
   [app.test-utils :as tu]
   [clojure.test :refer [deftest is testing use-fixtures]]
   [java-time.api :as t]))

(use-fixtures :once tu/db-fixture)
(use-fixtures :each tu/rollback-fixture)

(def ^:private test-cookie-name "test-session-id")
(def ^:private test-authz-id #uuid "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11") ; Use a real UUID
(def ^:private test-actor-id "test-actor-id@example.com") ; Use a real email
(def ^:private test-actor-data {:id test-actor-id :use-name "Test Actor" :enrollment-state "complete"}) ; Match DB columns
(def ^:private test-identity-data {:provider #pg_enum :identity-strategy/INVALID :provider-identity test-actor-id}) ; Match DB columns
(def ^:private test-authz-data (merge {:id test-authz-id :actor-id test-actor-id} test-identity-data)) ; Match DB columns

(defn- ->timestamp-in-future [{:keys [amount unit] :or {amount 1 unit :hours}}]
  (let [duration (case unit
                   :hours (t/hours amount)
                   :days (t/days amount))]
    (t/plus (t/instant) duration)))

(defn- ->timestamp-in-past [{:keys [amount unit] :or {amount 1 unit :hours}}]
  (let [duration (case unit
                   :hours (t/hours amount)
                   :days (t/days amount))]
    (t/minus (t/instant) duration)))

(def ^:private actor-id-ga "get-actor-test-actor@example.com")
(def ^:private actor-data-ga {:id actor-id-ga :use-name "GA Actor" :enrollment-state "active"})

;; Mimic structure from test-identity-data for provider fields in AppAuthorization
(def ^:private identity-fields-ga {:provider #pg_enum :identity-strategy/INVALID
                                   :provider-identity (str "ga-provider-id-" (random-uuid))})

(def ^:private valid-authz-id-ga (random-uuid))
(def ^:private valid-authz-data-ga
  (merge {:id valid-authz-id-ga
          :actor-id actor-id-ga
          :expires-at (->timestamp-in-future {:amount 1 :unit :hours})}
         identity-fields-ga))

(def ^:private expired-authz-id-ga (random-uuid))
(def ^:private expired-authz-data-ga
  (merge {:id expired-authz-id-ga
          :actor-id actor-id-ga
          :expires-at (->timestamp-in-past {:amount 1 :unit :hours})}
         identity-fields-ga))

(def ^:private non-existent-authz-id-ga (random-uuid))

(deftest wrap-current-actor-test
  (testing "when cookie exists and actor is found"
    (tu/with-inserted-data [::models/Actor test-actor-data
                            ::models/Identity test-identity-data
                            ::models/AppAuthorization test-authz-data]
      (let [request {:cookies {test-cookie-name {:value (str test-authz-id)}}} ; Cookie value must be string
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
    (let [request {:cookies {test-cookie-name {:value (str test-authz-id)}}} ; Use the ID, but don't insert it
          handler-called? (atom false)
          handler (fn [req]
                    (reset! handler-called? true)
                    (is (= request req) "Request should not have :current-actor")
                    {:status 200})
          wrapped-handler (wrap-current-actor handler {:cookie-name test-cookie-name})]
      (wrapped-handler request)
      (is @handler-called? "Handler should be called")))

  (testing "when cookie exists but actor is not found "
    (let [request {:cookies {test-cookie-name {:value (str test-authz-id)}}}
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

(deftest get-actor!-auth-exists-test
  (testing "when authorization is valid and actor exists"
    (tu/with-inserted-data [::models/Actor actor-data-ga
                            ::models/Identity identity-fields-ga
                            ::models/AppAuthorization valid-authz-data-ga]
      (let [actor (get-actor! valid-authz-id-ga)]
        (is (some? actor) "Actor should be found")
        (is (= actor-id-ga (:id actor)))
        (is (= (:use-name actor-data-ga) (:use-name actor)))
        (is (some? (:created-at actor)))
        (is (some? (:updated-at actor)))))))

(deftest get-actor!-auth-not-exists-test
 (testing "when authorization does not exist"
   (tu/with-inserted-data [::models/Actor actor-data-ga] ; Actor exists, but no matching authz
     (let [actor (get-actor! non-existent-authz-id-ga)]
       (is (nil? actor) "Actor should not be found for a non-existent authorization ID")))))

(deftest get-actor!-auth-expired-test
  (testing "when authorization is expired"
    (tu/with-inserted-data [::models/Actor actor-data-ga
                            ::models/Identity identity-fields-ga
                            ::models/AppAuthorization expired-authz-data-ga]
      (let [actor (get-actor! expired-authz-id-ga)]
        (is (nil? actor) "Actor should not be found due to expired authorization")))))
