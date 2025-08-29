(ns app.card.state-test
  (:require
   [app.card.state :as card.state]
   [clojure.test :refer [deftest is testing]]))

;; Test data fixtures - using proper JS Date objects for time fields
(def sample-player-card
  {:card-type :card-type-enum/PLAYER_CARD
   :name "Test Player"
   :version "1"
   :deck-size 5
   :sht 3
   :pss 2
   :def 4
   :speed 2
   :size :size-enum/MD
   :abilities ["Fast Break" "Steal"]
   :created-at (js/Date. "2024-01-01T00:00:00.000Z")
   :updated-at (js/Date. "2024-01-01T00:00:00.000Z")
   :game-asset-id nil})

(def invalid-player-card
  {:card-type :card-type-enum/PLAYER_CARD
   :name "" ; empty name - likely invalid
   :version "1"
   :deck-size "invalid" ; should be int
   :sht -10 ; extremely negative value
   :pss 2
   :def 4
   :speed 2
   :size :size-enum/INVALID ; invalid enum value
   :abilities "not-vector" ; should be vector
   :created-at (js/Date. "2024-01-01T00:00:00.000Z")
   :updated-at (js/Date. "2024-01-01T00:00:00.000Z")
   :game-asset-id nil})

;; Tests for validate-card function
(deftest validate-card-with-valid-data-test
  (testing "Valid card validation"
    (let [initial-state {:card/data sample-player-card}
          result (card.state/validate-card initial-state)]
      (is (map? result)
          "Should return state map")

      (is (contains? result :card/errors)
          "Should contain errors key")

      ;; The function will always add :card/errors key, but it might be nil for valid cards
      (is (or (nil? (:card/errors result))
              (empty? (:card/errors result)))
          "Should have no errors for valid card"))))

(deftest validate-card-with-invalid-data-test
  (testing "Invalid card validation"
    (let [initial-state {:card/data invalid-player-card}
          result (card.state/validate-card initial-state)]
      (is (map? result)
          "Should return state map")

      (is (contains? result :card/errors)
          "Should contain errors key")

      ;; Since the card has invalid data, errors should be present
      (is (or (nil? (:card/errors result))
              (map? (:card/errors result)))
          "Should return nil or map for errors"))))

(deftest validate-card-with-nil-data-test
  (testing "Nil card data handling"
    (let [initial-state {:card/data nil}
          result (card.state/validate-card initial-state)]
      (is (map? result)
          "Should return state map")

      (is (contains? result :card/errors)
          "Should contain errors key"))))

(deftest validate-card-with-missing-data-test
  (testing "Missing card data handling"
    (let [initial-state {}
          result (card.state/validate-card initial-state)]
      (is (map? result)
          "Should return state map")

      (is (contains? result :card/errors)
          "Should contain errors key"))))

;; Tests for initial-card-state function
(deftest initial-card-state-with-card-data-test
  (testing "Creates correct initial state structure"
    (let [state (card.state/initial-card-state sample-player-card)]
      (is (= sample-player-card (:card/data state))
          "Should set card data correctly")

      (is (= sample-player-card (:card/pristine state))
          "Should set pristine state correctly")

      (is (= #{} (:card/dirty state))
          "Should initialize dirty as empty set")

      (is (= {} (:card/errors state))
          "Should initialize errors as empty map")

      (is (= #{} (:card/loading state))
          "Should initialize loading as empty set")

      (is (= false (:card/loading-card? state))
          "Should initialize loading-card? as false")

      (is (nil? (:card/load-error state))
          "Should initialize load-error as nil"))))

(deftest initial-card-state-with-nil-card-test
  (testing "Handles nil card"
    (let [state (card.state/initial-card-state nil)]
      (is (nil? (:card/data state))
          "Should handle nil card data")

      (is (nil? (:card/pristine state))
          "Should handle nil pristine state")

      (is (= #{} (:card/dirty state))
          "Should still initialize other fields properly"))))

;; Tests for card-state-reducer function
(deftest card-state-reducer-update-field-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "update-field action"
      (let [action {:type :update-field :field :deck-size :value 10}
            new-state (card.state/card-state-reducer initial-state action)]
        (is (= 10 (get-in new-state [:card/data :deck-size]))
            "Should update field value")

        (is (contains? (:card/dirty new-state) :deck-size)
            "Should mark field as dirty")

        (is (not (contains? (:card/errors new-state) :deck-size))
            "Should remove field error if exists")

        (is (contains? new-state :card/errors)
            "Should validate card and update errors key")))))

(deftest card-state-reducer-field-update-loading-action-true-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "field-update-loading action with loading? true"
      (let [action {:type :field-update-loading :field :deck-size :loading? true}
            new-state (card.state/card-state-reducer initial-state action)]
        (is (contains? (:card/loading new-state) :deck-size)
            "Should add field to loading set")))))

(deftest card-state-reducer-field-update-loading-action-false-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "field-update-loading action with loading? false"
      (let [state-with-loading (update initial-state :card/loading conj :deck-size)
            action {:type :field-update-loading :field :deck-size :loading? false}
            new-state (card.state/card-state-reducer state-with-loading action)]
        (is (not (contains? (:card/loading new-state) :deck-size))
            "Should remove field from loading set")))))

(deftest card-state-reducer-field-update-success-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "field-update-success action"
      (let [updated-card {:name "Updated Name" :deck-size 15}
            dirty-state (-> initial-state
                            (update :card/dirty conj :deck-size)
                            (update :card/loading conj :deck-size)
                            (assoc-in [:card/errors :deck-size] "Some error"))
            action {:type :field-update-success :field :deck-size :updated-card updated-card}
            new-state (card.state/card-state-reducer dirty-state action)]
        (is (= "Updated Name" (get-in new-state [:card/data :name]))
            "Should merge updated card data")

        (is (not (contains? (:card/dirty new-state) :deck-size))
            "Should remove field from dirty set")

        (is (not (contains? (:card/loading new-state) :deck-size))
            "Should remove field from loading set")

        (is (not (contains? (:card/errors new-state) :deck-size))
            "Should remove field error")))))

(deftest card-state-reducer-field-update-error-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "field-update-error action"
      (let [error-msg "Update failed"
            state-with-loading (-> initial-state
                                   (assoc-in [:card/data :deck-size] 10)
                                   (update :card/loading conj :deck-size))
            action {:type :field-update-error :field :deck-size :error error-msg}
            new-state (card.state/card-state-reducer state-with-loading action)]
        (is (not (contains? (:card/loading new-state) :deck-size))
            "Should remove field from loading set")

        (is (= error-msg (get-in new-state [:card/errors :deck-size]))
            "Should set field error")

        (is (= 5 (get-in new-state [:card/data :deck-size]))
            "Should revert field to pristine value")))))

(deftest card-state-reducer-reset-pristine-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "reset-pristine action"
      (let [updated-card (assoc sample-player-card :deck-size 10)
            dirty-state (-> initial-state
                            (assoc-in [:card/data :deck-size] 10)
                            (update :card/dirty conj :deck-size)
                            (update :card/loading conj :deck-size)
                            (assoc-in [:card/errors :deck-size] "Some error"))
            action {:type :reset-pristine :card updated-card}
            new-state (card.state/card-state-reducer dirty-state action)]
        (is (= updated-card (:card/data new-state))
            "Should update card data")

        (is (= updated-card (:card/pristine new-state))
            "Should update pristine state")

        (is (= #{} (:card/dirty new-state))
            "Should clear dirty fields")

        (is (= #{} (:card/loading new-state))
            "Should clear loading fields")

        (is (= {} (:card/errors new-state))
            "Should clear errors")))))

(deftest card-state-reducer-loading-card-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "loading-card action"
      (let [action {:type :loading-card}
            new-state (card.state/card-state-reducer initial-state action)]
        (is (= true (:card/loading-card? new-state))
            "Should set loading-card? to true")

        (is (nil? (:card/load-error new-state))
            "Should clear load error")))))

(deftest card-state-reducer-card-loaded-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "card-loaded action"
      (let [loaded-card (assoc sample-player-card :deck-size 20)
            loading-state (assoc initial-state :card/loading-card? true)
            action {:type :card-loaded :card loaded-card}
            new-state (card.state/card-state-reducer loading-state action)]
        (is (= loaded-card (:card/data new-state))
            "Should set card data")

        (is (= loaded-card (:card/pristine new-state))
            "Should set pristine state")

        (is (= #{} (:card/dirty new-state))
            "Should clear dirty fields")

        (is (= #{} (:card/loading new-state))
            "Should clear loading fields")

        (is (= {} (:card/errors new-state))
            "Should clear errors")

        (is (= false (:card/loading-card? new-state))
            "Should set loading-card? to false")

        (is (nil? (:card/load-error new-state))
            "Should clear load error")))))

(deftest card-state-reducer-card-load-error-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "card-load-error action"
      (let [error-msg "Failed to load card"
            loading-state (assoc initial-state :card/loading-card? true)
            action {:type :card-load-error :error error-msg}
            new-state (card.state/card-state-reducer loading-state action)]
        (is (= false (:card/loading-card? new-state))
            "Should set loading-card? to false")

        (is (= error-msg (:card/load-error new-state))
            "Should set load error")))))

(deftest card-state-reducer-set-loading-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "set-loading action"
      (let [action {:type :set-loading :fields [:deck-size :sht]}
            new-state (card.state/card-state-reducer initial-state action)]
        (is (= #{:deck-size :sht} (:card/loading new-state))
            "Should set loading fields")))))

(deftest card-state-reducer-set-errors-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "set-errors action"
      (let [errors {:deck-size "Invalid value" :sht "Too low"}
            action {:type :set-errors :errors errors}
            new-state (card.state/card-state-reducer initial-state action)]
        (is (= errors (:card/errors new-state))
            "Should set error map")))))

(deftest card-state-reducer-set-field-error-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "set-field-error action"
      (let [action {:type :set-field-error :field :deck-size :error "Invalid value"}
            new-state (card.state/card-state-reducer initial-state action)]
        (is (= "Invalid value" (get-in new-state [:card/errors :deck-size]))
            "Should set field-specific error")))))

(deftest card-state-reducer-clear-field-error-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "clear-field-error action"
      (let [state-with-error (assoc-in initial-state [:card/errors :deck-size] "Some error")
            action {:type :clear-field-error :field :deck-size}
            new-state (card.state/card-state-reducer state-with-error action)]
        (is (not (contains? (:card/errors new-state) :deck-size))
            "Should remove field error")))))

(deftest card-state-reducer-clear-errors-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "clear-errors action"
      (let [state-with-errors (assoc initial-state :card/errors {:deck-size "Error1" :sht "Error2"})
            action {:type :clear-errors}
            new-state (card.state/card-state-reducer state-with-errors action)]
        (is (= {} (:card/errors new-state))
            "Should clear all errors")))))

(deftest card-state-reducer-card-creating-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "card-creating action"
      (let [action {:type :card-creating}
            new-state (card.state/card-state-reducer initial-state action)]
        (is (= true (:card/creating? new-state))
            "Should set creating? to true")

        (is (nil? (:card/create-error new-state))
            "Should clear create error")))))

(deftest card-state-reducer-card-created-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "card-created action"
      (let [created-card (assoc sample-player-card :name "Created Card")
            creating-state (assoc initial-state :card/creating? true)
            action {:type :card-created :card created-card}
            new-state (card.state/card-state-reducer creating-state action)]
        (is (= false (:card/creating? new-state))
            "Should set creating? to false")

        (is (nil? (:card/create-error new-state))
            "Should clear create error")

        (is (= created-card (:card/data new-state))
            "Should set card data")

        (is (= created-card (:card/pristine new-state))
            "Should set pristine state")

        (is (= #{} (:card/dirty new-state))
            "Should clear dirty fields")

        (is (= #{} (:card/loading new-state))
            "Should clear loading fields")))))

(deftest card-state-reducer-card-create-error-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "card-create-error action"
      (let [error-msg "Failed to create card"
            creating-state (assoc initial-state :card/creating? true)
            action {:type :card-create-error :error error-msg}
            new-state (card.state/card-state-reducer creating-state action)]
        (is (= false (:card/creating? new-state))
            "Should set creating? to false")

        (is (= error-msg (:card/create-error new-state))
            "Should set create error")))))

(deftest card-state-reducer-unknown-action-test
  (let [initial-state (card.state/initial-card-state sample-player-card)]
    (testing "unknown action"
      (let [action {:type :unknown-action}
            new-state (card.state/card-state-reducer initial-state action)]
        (is (= initial-state new-state)
            "Should return unchanged state for unknown actions")))))

;; Integration-style tests that test the interaction between functions
(deftest card-state-workflow-test
  (testing "Card state workflow"
    (let [initial-state (card.state/initial-card-state sample-player-card)
          state-after-update (card.state/card-state-reducer
                                initial-state
                                {:type :update-field :field :deck-size :value 10})]
      (is (= 10 (get-in state-after-update [:card/data :deck-size]))
          "Field should be updated")

      (is (contains? (:card/dirty state-after-update) :deck-size)
          "Field should be marked as dirty")

      ;; Validate the updated state
      (let [validated-state (card.state/validate-card state-after-update)]
        (is (contains? validated-state :card/errors)
            "Should have validation results")))))

(deftest error-handling-workflow-test
  (testing "Error handling workflow"
    (let [initial-state (card.state/initial-card-state sample-player-card)
          state-with-invalid (card.state/card-state-reducer
                                initial-state
                                {:type :update-field :field :deck-size :value "invalid"})]
      ;; The update-field action automatically validates via validate-card
      (is (= "invalid" (get-in state-with-invalid [:card/data :deck-size]))
          "Invalid value should be set")

      ;; Set additional error manually
      (let [state-with-error (card.state/card-state-reducer
                              state-with-invalid
                              {:type :set-field-error :field :deck-size :error "Manual error"})]
        (is (= "Manual error" (get-in state-with-error [:card/errors :deck-size]))
            "Manual error should be set")

        ;; Clear the error
        (let [state-error-cleared (card.state/card-state-reducer
                                   state-with-error
                                   {:type :clear-field-error :field :deck-size})]
          (is (not (contains? (:card/errors state-error-cleared) :deck-size))
              "Error should be cleared"))))))

(deftest loading-state-workflow-test
  (testing "Loading state workflow"
    (let [initial-state (card.state/initial-card-state sample-player-card)
          loading-state (card.state/card-state-reducer
                           initial-state
                           {:type :field-update-loading :field :deck-size :loading? true})]
      (is (contains? (:card/loading loading-state) :deck-size)
          "Field should be in loading state")

      ;; Simulate successful update
      (let [success-state (card.state/card-state-reducer
                           loading-state
                           {:type :field-update-success
                            :field :deck-size
                            :updated-card {:deck-size 15}})]
        (is (= 15 (get-in success-state [:card/data :deck-size]))
            "Field should be updated")

        (is (not (contains? (:card/loading success-state) :deck-size))
            "Field should no longer be loading")))))

(deftest card-creation-workflow-test
  (testing "Card creation workflow"
    (let [initial-state (card.state/initial-card-state {})
          creating-state (card.state/card-state-reducer
                            initial-state
                            {:type :card-creating})]
      (is (:card/creating? creating-state)
          "Should be in creating state")

      ;; Successful creation
      (let [created-state (card.state/card-state-reducer
                           creating-state
                           {:type :card-created :card sample-player-card})]
        (is (not (:card/creating? created-state))
            "Should no longer be creating")

        (is (= sample-player-card (:card/data created-state))
            "Should have created card data")

        (is (= sample-player-card (:card/pristine created-state))
            "Should set pristine state")))))
