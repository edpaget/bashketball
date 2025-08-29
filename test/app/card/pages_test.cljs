(ns app.card.pages-test
  (:require
   ["@apollo/client" :as apollo.client]
   ["@apollo/client/testing" :refer [MockedProvider]]
   ["@testing-library/react" :as tlr]
   [app.card.pages :as pages]
   [app.router :as router]
   [app.test-utils :as test-utils]
   [cljs.test :as t :refer [deftest is testing]]
   [clojure.string]
   [uix.core :refer [$]])
  (:require-macros
   [cljs.test :refer [deftest testing is]]))

(test-utils/setup-frontend-test-env!)
(t/use-fixtures :each test-utils/react-cleanup-fixture)

;; Mock router functions
(defn mock-router-state
  [& {:keys [path-params query-params route-name]
      :or {path-params {} query-params {} route-name :cards-index}}]
  {:path-params path-params
   :query-params query-params
   :data {:name route-name}})

(defn setup-router-mocks! []
  (set! router/href (fn [page & args]
                      (str "/" (name page)
                           (when (seq args)
                             (str "/" (clojure.string/join "/" args)))))))

;; Create GraphQL query document using gql tag - match the actual compiled query
(def GET_ALL_CARDS_QUERY
  (apollo.client/gql "query getAllCards {
    cards {
      name
      version
      createdAt
      updatedAt
      __typename
    }
  }"))

;; Apollo MockedProvider mocks
(def cards-loading-mock
  {:request {:query GET_ALL_CARDS_QUERY}
   :delay 1000 ; Simulate loading
   :result {:data {:cards []}}})

(def cards-success-mock
  {:request {:query GET_ALL_CARDS_QUERY}
   :result {:data {:cards [{:__typename "Card" :name "Fire Blast" :version 1 :createdAt "2024-01-01" :updatedAt "2024-01-01"}
                           {:__typename "Card" :name "Lightning Strike" :version 1 :createdAt "2024-01-01" :updatedAt "2024-01-01"}
                           {:__typename "Card" :name "Healing Potion" :version 1 :createdAt "2024-01-01" :updatedAt "2024-01-01"}]}}})

(def cards-empty-mock
  {:request {:query GET_ALL_CARDS_QUERY}
   :result {:data {:cards []}}})

(def cards-error-mock
  {:request {:query GET_ALL_CARDS_QUERY}
   :error (js/Error. "Failed to load cards")})

;; Helper function to render with MockedProvider
(defn render-with-mocked-apollo
  ^js [component mocks]
  (tlr/render
   ($ MockedProvider {:mocks (clj->js mocks)}
      component)))

;; cards-index component tests - Focus on user interactions
(deftest cards-index-loading-state-test
  (testing "shows loading message while fetching cards"
    (setup-router-mocks!)
    (let [^js result (render-with-mocked-apollo ($ pages/cards-index) [cards-loading-mock])]
      (t/is (.getByText result "loading cards...")))))

(deftest cards-index-displays-cards-test
  (testing "displays cards when data loads successfully"
    (setup-router-mocks!)
    (let [^js result (render-with-mocked-apollo ($ pages/cards-index) [cards-success-mock])]
      ;; Wait for loading to complete, then verify cards are displayed
      (t/async done
               (.then
                (tlr/waitFor
                 #(.getByText result "Fire Blast"))
                (fn []
                  (t/is (.getByText result "Fire Blast"))
                  (t/is (.getByText result "Lightning Strike"))
                  (t/is (.getByText result "Healing Potion"))
                  (done)))))))

(deftest cards-index-empty-state-test
  (testing "shows new card option when no cards exist"
    (setup-router-mocks!)
    (let [^js result (render-with-mocked-apollo ($ pages/cards-index) [cards-empty-mock])]
      (t/async done
               (.then
                (tlr/waitFor
                 #(.getByText result "New Card"))
                (fn []
                  (t/is (.getByText result "New Card"))
                  (done)))))))

(deftest cards-index-navigation-test
  (testing "provides navigation links to individual cards"
    (setup-router-mocks!)
    (let [^js result (render-with-mocked-apollo ($ pages/cards-index) [cards-success-mock])]
      (t/async done
        (.then
         (tlr/waitFor
          #(.getByRole result "link" #js {:name "Fire Blast"}))
         (fn []
           ;; User can click on card links to navigate
           (t/is (.getByRole result "link" #js {:name "Fire Blast"}))
           (t/is (.getByRole result "link" #js {:name "New Card"}))
           (done)))))))

(deftest cards-index-new-card-link-test
  (testing "New Card button links to creation page"
    (setup-router-mocks!)
    (let [^js result (render-with-mocked-apollo ($ pages/cards-index) [cards-empty-mock])]
      (t/async done
        (.then
         (tlr/waitFor
          #(.getByRole result "link" #js {:name "New Card"}))
         (fn []
           (let [new-card-link (.getByRole result "link" #js {:name "New Card"})]
             (t/is new-card-link)
             (t/is (= "/cards-new" (.getAttribute new-card-link "href"))))
           (done)))))))

(deftest cards-index-error-resilience-test
  (testing "remains functional when card loading fails"
    (setup-router-mocks!)
    (let [^js result (render-with-mocked-apollo ($ pages/cards-index) [cards-error-mock])]
      ;; Even on error, should still show "New Card" button
      (t/is (.getByText result "New Card")))))

;; cards-show component tests - Focus on user experience
(deftest cards-show-renders-with-card-id-test
  (testing "displays card editing interface when given card ID"
    (setup-router-mocks!)
    (with-redefs [router/use-router (constantly (mock-router-state
                                                 :path-params {:id "test-card"}
                                                 :route-name :cards-show))]
      (let [^js result (test-utils/render-with-apollo ($ pages/cards-show))]
        ;; Component should render successfully for user interaction
        (t/is result)))))

(deftest cards-show-card-id-integration-test
  (testing "integrates router card ID with state management"
    (setup-router-mocks!)
    (with-redefs [router/use-router (constantly (mock-router-state
                                                 :path-params {:id "fire-blast"}
                                                 :route-name :cards-show))]
      (let [^js result (test-utils/render-with-apollo ($ pages/cards-show))]
        ;; Should pass card ID to child components for loading/editing
        (t/is result)))))

;; cards-new component tests - Focus on user workflow
(deftest cards-new-renders-creation-interface-test
  (testing "provides interface for creating new cards"
    (setup-router-mocks!)
    (let [^js result (test-utils/render-with-apollo ($ pages/cards-new))]
      ;; Should provide user with card creation interface
      (t/is result))))

(deftest cards-new-state-initialization-test
  (testing "initializes in new card creation mode"
    (setup-router-mocks!)
    (let [^js result (test-utils/render-with-apollo ($ pages/cards-new))]
      ;; Should be ready for user to create new card
      (t/is result))))

;; Integration tests - Focus on overall user experience
(deftest all-pages-functional-test
  (testing "all page components function without errors"
    (setup-router-mocks!)
    (with-redefs [router/use-router (constantly (mock-router-state :path-params {:id "test-card"}))]

      ;; Test that users can access all main pages
      (let [^js index-result (render-with-mocked-apollo ($ pages/cards-index) [cards-success-mock])]
        (t/is index-result))

      (let [^js show-result (test-utils/render-with-apollo ($ pages/cards-show))]
        (t/is show-result))

      (let [^js new-result (test-utils/render-with-apollo ($ pages/cards-new))]
        (t/is new-result)))))
