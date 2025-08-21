(ns app.test-utils.state
  "Test utilities specifically for state management testing"
  (:require
   [app.card.state :as card.state]
   [app.test-utils :as test-utils]
   [uix.core :refer [$ defui]]

   ["@testing-library/react" :as tlr]))

;; State Testing Wrappers
;; =============================================================================

(defui state-test-wrapper
  "Test wrapper that provides both Apollo and card state contexts"
  [{:keys [children card-options client]
    :or {card-options {:new? true}
         client test-utils/mock-apollo-client}}]
  ($ test-utils/apollo-test-wrapper {:client client}
     ($ card.state/with-card card-options
        children)))

;; Convenience Functions
;; =============================================================================

(defn render-with-state
  "Convenience function to render a component with both Apollo and card state contexts"
  ([component] (render-with-state component {}))
  ([component {:keys [card-options client] :as opts}]
   (tlr/render
    ($ state-test-wrapper {:card-options card-options :client client}
       component))))

(defn render-new-card-component
  "Render a component with new card state context"
  [component]
  (render-with-state component {:card-options {:new? true}}))

(defn render-existing-card-component
  "Render a component with existing card state context"
  [component {:keys [card-name version]
              :or {card-name "test-card" version 1}}]
  (render-with-state component {:card-options {:card-name card-name :version version}}))

;; State Assertion Helpers
;; =============================================================================

(defn assert-field-value
  "Assert that a field has the expected value in a rendered component"
  [result field-name expected-value]
  (let [field (.queryByDisplayValue result expected-value)]
    (not (nil? field))))

(defn assert-field-exists
  "Assert that a field with given label exists in a rendered component"
  [result field-label]
  (let [field (.queryByLabelText result field-label)]
    (not (nil? field))))

(defn assert-button-exists
  "Assert that a button with given name exists in a rendered component"
  [result button-name]
  (let [button (.queryByRole result "button" #js {:name button-name})]
    (not (nil? button))))

(defn click-button
  "Click a button with the given name in a rendered component"
  [result button-name]
  (when-let [button (.queryByRole result "button" #js {:name button-name})]
    (tlr/fireEvent.click button)
    true))

;; Usage Examples (for documentation)
;; =============================================================================

(comment
  ;; Basic usage in a state test file:

  (ns my.state-test
    (:require
     [app.test-utils.state :as state-utils]
     [cljs.test :as t :include-macros true]))

  ;; Test new card state
  (t/deftest test-new-card-state
    (let [result (state-utils/render-new-card-component
                  ($ my-card-component))]
      (t/is (state-utils/assert-button-exists result "Create Card"))))

  ;; Test existing card state
  (t/deftest test-existing-card-state
    (let [result (state-utils/render-existing-card-component
                  ($ my-card-component)
                  {:card-name "my-card" :version 2})]
      (t/is (state-utils/assert-field-value result "name" "my-card"))))

  ;; Test interactions
  (t/deftest test-button-interaction
    (let [spy (test-utils/with-card-operation-spy)
          result (state-utils/render-new-card-component
                  ($ my-interactive-component))]

      (state-utils/click-button result "Save Card")

      (t/is (> (:update-calls @(:state spy)) 0)))))
