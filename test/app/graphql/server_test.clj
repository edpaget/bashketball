(ns app.graphql.server-test
  (:require
   [app.graphql.server :as gql.server]
   [clojure.test :refer [deftest is testing]]
   [integrant.core :as ig]
   [malli.experimental :as me]))

;; --- Test Setup ---

(me/defn hello-resolver :- [:map {:graphql/type "hello-msg"} [:msg :string]]
  [_ :- :any _ :- :any _ :- :any]
  {:msg "Hello World"})

(def test-resolvers
  {:Query/hello [(-> #'hello-resolver meta :schema) hello-resolver]})

;; --- Tests ---

(deftest handler-init-key-test
  (testing "Integrant init-key for ::handler"
    (let [handler (ig/init-key ::gql.server/handler {:resolvers test-resolvers})
          request {:body {:query "query { hello { msg } }"
                          :variables {}}}
          response (handler request)]
      (is (= 200 (:status response)) "Response status should be 200")
      (is (= "application/json" (get-in response [:headers "Content-Type"]))
          "Content-Type header should be application/json")
      (is (= {:data {:hello {:msg "Hello World"}}}
             (:body response))
          "Response body should contain the correct GraphQL result"))))
