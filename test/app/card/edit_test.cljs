(ns app.card.edit-test
  (:require
   [app.card.edit :as sut]
   [app.card.state :as card.state]
   [app.test-utils :as test-utils]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]

   ["@testing-library/react" :as tlr]))

;; Set up test environment once
(test-utils/setup-frontend-test-env!)

;; Use React cleanup fixture
(t/use-fixtures :each test-utils/react-cleanup-fixture)

(t/deftest test-edit-card-renders-new
  ;; Render with Apollo context and card state context for new card
  (let [result (test-utils/render-with-apollo
                ($ card.state/with-card {:new? true}
                   ($ sut/edit-card)))]

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
  ;; Set up spy to track create calls
  (let [spy (test-utils/with-card-operation-spy)]

    ;; Render component
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ sut/edit-card)))]

      ;; Find and click the create button
      (when-let [create-button (.queryByRole result "button" #js {:name "Create Card"})]
        (tlr/fireEvent.click create-button))

      ;; Check that create was called
      (t/is (> (:create-calls @(:state spy)) 0)
            "Create function should have been called when button clicked"))))

(t/deftest test-edit-card-with-custom-data
  ;; Set up custom test data
  (test-utils/mock-card-operations-with-data!
   {:card-data {:name "Existing Card"
                :version 1
                :card-type :card-type-enum/PLAYER_CARD}})

  ;; Render component with existing card context
  (let [result (test-utils/render-with-apollo
                ($ card.state/with-card {:card-name "existing-card" :version 1}
                   ($ sut/edit-card)))]

    ;; Verify card data is displayed
    (t/is (not (nil? (.queryByDisplayValue result "Existing Card")))
          "Should display existing card name")))
