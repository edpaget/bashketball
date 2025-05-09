(ns app.actor-test
  (:require
   [app.actor :as actor]
   [clojure.test :refer [deftest is testing]]
   [java-time.api :as t]))

(deftest current-actor-test
  (testing "when current-actor exists in request context"
    (let [expected-actor {:id "test-actor-id"
                          :enrollment-state "test"
                          :use-name "Test Actor"
                          :created-at (t/instant)
                          :updated-at (t/instant)}
          context {:request {:current-actor expected-actor}}]
      (is (= expected-actor (actor/current-actor context nil nil)))))

  (testing "when current-actor does not exist in request context"
    (let [context {:request {}}]
      (is (nil? (actor/current-actor context nil nil))))

    (let [context {:request {:current-actor nil}}]
      (is (nil? (actor/current-actor context nil nil))))))
