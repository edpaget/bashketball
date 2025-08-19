(ns app.card.edit-test
  (:require
   [app.card.edit :as sut]
   [app.card.state :as card.state]
   [app.card.graphql-operations :as card.operations]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$ defui]]
   ["@apollo/client" :as apollo.client]
   ["@testing-library/react" :as tlr]
   ["jsdom" :as jsdom]))

;; Set up JSDOM environment for React Testing Library
(defn setup-jsdom! []
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

;; Initialize JSDOM before any tests run
(setup-jsdom!)

(def react-cleanup-fixture
  {:after (fn [] (tlr/cleanup))})

(t/use-fixtures :each react-cleanup-fixture)

;; Mock Apollo Client
(def mock-client
  (apollo.client/ApolloClient.
   (clj->js {:cache (apollo.client/InMemoryCache.)
             :defaultOptions {:mutate {:errorPolicy "all"}
                              :query {:errorPolicy "all"}}
             :link (apollo.client/from
                    #js [(apollo.client/ApolloLink.
                          (fn [operation forward]
                            ;; Mock handler for GraphQL operations
                            (js/Promise.resolve
                             (clj->js {:data {}}))))])})))

;; Test wrapper component that provides Apollo context
(defui test-wrapper
  [{:keys [children]}]
  ($ apollo.client/ApolloProvider {:client mock-client}
     children))

;; Mock the direct GraphQL operations to avoid actual network calls
(defn mock-card-operations! []
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
           {:loading false :error nil}])))

(t/deftest test-edit-card-renders-new
  ;; Set up mocks before rendering
  (mock-card-operations!)

  ;; Render with both Apollo context and card state context for new card
  (let [result (tlr/render
                ($ test-wrapper
                   ($ card.state/with-card {:new? true}
                      ($ sut/edit-card))))]

    ;; Verify that the component renders with expected text for new card
    (t/is (not (nil? (.queryByRole result "heading" #js {:name "Create Card"})))
          "Should show 'Create Card' heading for new card")

    ;; Verify form fields exist
    (t/is (not (nil? (.queryByLabelText result "Card Type")))
          "Should have Card Type field")
    (t/is (not (nil? (.queryByLabelText result "Name")))
          "Should have Name field")

    ;; Verify the create button exists
    (t/is (not (nil? (.queryByRole result "button" #js {:name "Create Card"})))
          "Should have Create Card button")))

(t/deftest test-create-button-clicks
  ;; Set up mocks
  (mock-card-operations!)

  ;; Track if create was called
  (let [create-called (atom false)]
    (set! card.operations/card-create
          (fn [type]
            [(fn [variables]
               (reset! create-called true)
               (js/Promise.resolve {:data {:createPlayerCard {:name "New Card"
                                                              :version 0
                                                              :card-type :card-type-enum/PLAYER_CARD}}}))
             {:loading false :error nil}]))

    ;; Render component
    (let [result (tlr/render
                  ($ test-wrapper
                     ($ card.state/with-card {:new? true}
                        ($ sut/edit-card))))]

      ;; Find and click the create button
      (when-let [create-button (.queryByRole result "button" #js {:name "Create Card"})]
        (tlr/fireEvent.click create-button))

      ;; Check that create was called
      (t/is @create-called "Create function should have been called when button clicked"))))
