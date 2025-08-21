(ns app.test-utils
  "ClojureScript test utilities for frontend testing"
  (:require
   [app.card.graphql-operations :as card.operations]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$ defui]]

   ["@apollo/client" :as apollo.client]
   ["@testing-library/react" :as tlr]
   ["jsdom" :as jsdom]))

;; JSDOM Setup
;; =============================================================================

(defn setup-jsdom!
  "Sets up JSDOM environment for React Testing Library.
   Should be called once before running tests."
  []
  (when (and (exists? js/global)
             (not (exists? js/document)))
    (let [dom (jsdom/JSDOM. "<!DOCTYPE html><html><body></body></html>"
                            #js {:url "http://localhost"
                                 :pretendToBeVisual true
                                 :resources "usable"})
          window (.-window dom)]
      ;; Set global properties
      (set! js/global.window window)
      (set! js/global.document (.-document window))
      (set! js/global.navigator (.-navigator window))
      (set! js/global.HTMLElement (.-HTMLElement window))
      ;; Also set on js/document for Testing Library
      (set! js/document (.-document window))
      ;; Copy window properties to global for React
      (doseq [key (.keys js/Object window)]
        (when (and (not (exists? (aget js/global key)))
                   (not= key "localStorage")
                   (not= key "sessionStorage"))
          (aset js/global key (aget window key)))))))

;; React Testing Fixtures
;; =============================================================================

(def react-cleanup-fixture
  "Test fixture that ensures React Testing Library cleanup after each test"
  {:after (fn [] (tlr/cleanup))})

;; Apollo Client Mocks
;; =============================================================================

(def mock-apollo-client
  "Mock Apollo Client for testing GraphQL components without network calls"
  (apollo.client/ApolloClient.
   (clj->js {:cache (apollo.client/InMemoryCache.)
             :defaultOptions {:mutate {:errorPolicy "all"}
                              :query {:errorPolicy "all"}}
             :link (apollo.client/createHttpLink
                    (clj->js {:uri "http://localhost:3000/test-graphql"
                              :fetch (fn [& args]
                                       ;; Mock fetch that returns empty GraphQL response
                                       (js/Promise.resolve
                                        (js/Response.
                                         (js/JSON.stringify (clj->js {:data {}}))
                                         (clj->js {:status 200
                                                   :headers {"Content-Type" "application/json"}}))))}))})))

(defui apollo-test-wrapper
  "Test wrapper component that provides Apollo context for testing"
  [{:keys [children client]
    :or {client mock-apollo-client}}]
  ($ apollo.client/ApolloProvider {:client client}
     children))

;; GraphQL Operation Mocks
;; =============================================================================

(defn mock-card-operations!
  "Mocks all card GraphQL operations to prevent actual network calls during testing.
   Call this in test setup to replace real operations with test stubs."
  []
  ;; Mock card-query to return a resolved promise with empty data
  (set! card.operations/card-query
        (fn [name version]
          (js/Promise.resolve {:data {:card nil}})))

  ;; Mock card-field-update to return a resolved promise
  (set! card.operations/card-field-update
        (fn [field exec-args]
          (js/Promise.resolve {:data {}})))

  ;; Mock card-create to return a tuple with a function that returns a promise
  (set! card.operations/card-create
        (fn [type]
          [(fn [variables]
             (js/Promise.resolve {:data {:createPlayerCard {:name "Test Card"
                                                            :version 0
                                                            :card-type :card-type-enum/PLAYER_CARD}}}))
           {:loading false :error nil}]))

  ;; Mock use-debounce hook to return the value immediately
  (set! card.operations/use-debounce
        (fn [value delay] value)))

(defn mock-card-operations-with-data!
  "Mocks card operations with custom test data.
   
   Options:
   - :card-data - Data to return from card-query
   - :create-response - Data to return from card-create
   - :update-response - Data to return from card-field-update"
  [{:keys [card-data create-response update-response]
    :or {card-data nil
         create-response {:createPlayerCard {:name "Test Card"
                                             :version 0
                                             :card-type :card-type-enum/PLAYER_CARD}}
         update-response {}}}]

  (set! card.operations/card-query
        (fn [name version]
          (js/Promise.resolve {:data {:card card-data}})))

  (set! card.operations/card-field-update
        (fn [field exec-args]
          (js/Promise.resolve {:data update-response})))

  (set! card.operations/card-create
        (fn [type]
          [(fn [variables]
             (js/Promise.resolve {:data create-response}))
           {:loading false :error nil}])))

(defn with-card-operation-spy
  "Creates a spy function that tracks calls to card operations.
   Returns a map with the spy functions and access to call tracking.
   
   Usage:
   (let [spy (with-card-operation-spy)]
     ;; Run test code
     (is (= 1 (:create-calls @(:state spy))))
     (is (= [expected-args] (:create-args @(:state spy)))))"
  []
  (let [state (atom {:query-calls 0 :query-args []
                     :update-calls 0 :update-args []
                     :create-calls 0 :create-args []})]

    (set! card.operations/card-query
          (fn [& args]
            (swap! state (fn [s] (-> s
                                     (update :query-calls inc)
                                     (update :query-args conj args))))
            (js/Promise.resolve {:data {:card nil}})))

    (set! card.operations/card-field-update
          (fn [& args]
            (swap! state (fn [s] (-> s
                                     (update :update-calls inc)
                                     (update :update-args conj args))))
            (js/Promise.resolve {:data {}})))

    (set! card.operations/card-create
          (fn [& args]
            (swap! state (fn [s] (-> s
                                     (update :create-calls inc)
                                     (update :create-args conj args))))
            [(fn [variables]
               (js/Promise.resolve {:data {:createPlayerCard {:name "Test Card"
                                                              :version 0
                                                              :card-type :card-type-enum/PLAYER_CARD}}}))
             {:loading false :error nil}]))

    {:state state
     :reset-spy! (fn [] (reset! state {:query-calls 0 :query-args []
                                       :update-calls 0 :update-args []
                                       :create-calls 0 :create-args []}))}))

;; Convenience Functions
;; =============================================================================

(defn render-with-apollo
  "Convenience function to render a component with Apollo context"
  ([component] (render-with-apollo component {}))
  ([component {:keys [client] :as opts}]
   (tlr/render
    ($ apollo-test-wrapper (if client {:client client} {})
       component))))

;; Test Setup Helpers
;; =============================================================================

(defn setup-frontend-test-env!
  "Complete setup for frontend testing environment.
   Call this once at the beginning of test files that need React/Apollo testing."
  []
  (setup-jsdom!)
  (mock-card-operations!)
  ;; Load Apollo error messages for better debugging
  (when (exists? js/goog.DEBUG)
    (try
      (let [apollo-dev (js/require "@apollo/client/dev")]
        (.loadDevMessages apollo-dev)
        (.loadErrorMessages apollo-dev))
      (catch js/Error e
        (js/console.log "Could not load Apollo dev messages:" e)))))

;; Usage Examples (for documentation)
;; =============================================================================

(comment
  ;; Basic usage in a test file:

  (ns my.component-test
    (:require
     [app.test-utils :as test-utils]
     [cljs.test :as t :include-macros true]))

  ;; Setup environment once
  (test-utils/setup-frontend-test-env!)

  ;; Use fixture for cleanup
  (t/use-fixtures :each test-utils/react-cleanup-fixture)

  ;; Simple test
  (t/deftest test-my-component
    (let [result (test-utils/render-with-apollo
                  ($ my-component))]
      (t/is (not (nil? (.queryByText result "Expected Text"))))))

  ;; Test with custom data
  (t/deftest test-with-data
    (test-utils/mock-card-operations-with-data!
     {:card-data {:name "Test Card" :version 1}})

    (let [result (test-utils/render-with-apollo
                  ($ card-component))]
      ;; Test assertions...
      ))

  ;; Test with spy to track calls
  (t/deftest test-operations-called
    (let [spy (test-utils/with-card-operation-spy)]
      (let [result (test-utils/render-with-apollo
                    ($ interactive-component))]
        ;; Interact with component
        (tlr/fireEvent.click (.getByRole result "button"))

        ;; Check that operations were called
        (t/is (= 1 (:create-calls @(:state spy))))))))
