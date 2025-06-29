(ns app.actor.resolvers-test
  (:require
   [app.actor]
   [app.graphql.resolvers :as gql.resolvers]
   [clojure.test :refer [deftest is testing]]
   [java-time.api :as t]))

(def current-actor (gql.resolvers/get-resolver-fn 'app.actor :Query/me))

(deftest current-actor-test
  (testing "when current-actor exists in request context"
    (let [expected-actor {:id "test-actor-id"
                          :enrollment-state "test"
                          :use-name "Test Actor"
                          :created-at (t/instant)
                          :updated-at (t/instant)}
          context {:request {:current-actor expected-actor}}]
      (is (= expected-actor (current-actor context nil nil)))))

  (testing "when current-actor does not exist in request context"
    (let [context {:request {}}]
      (is (nil? (current-actor context nil nil))))

    (let [context {:request {:current-actor nil}}]
      (is (nil? (current-actor context nil nil))))))
