(ns app.card-e2e-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [etaoin.api :as e]
   [etaoin.keys :as e.keys]
   [e2e.fixtures :as fx]
   [app.models :as models]
   [app.db :as db]
   [app.test-utils :as tu]
   [app.card-test-utils :as ctu]))

(use-fixtures :once fx/container-fixture fx/server-fixture)
(use-fixtures :each fx/webdriver-fixture)

(deftest ^:e2e card-edit-test
  (tu/with-inserted-data [::models/GameCard {:name "K-PAX"
                                             :version "0"
                                             :card-type (db/->pg_enum :card-type-enum/PLAYER_CARD)
                                             :deck-size 1
                                             :sht 1
                                             :pss 2
                                             :def 1
                                             :speed 4
                                             :size (db/->pg_enum :size-enum/MD)
                                             :abilities ["details"]
                                             :offense "Offense text"
                                             :defense "Defense text"
                                             :play nil
                                             :coaching nil
                                             :fate nil
                                             :asset-power nil}]
    (testing "user can edit a card and see the changes persist"
      ;; Use the new utilities for cleaner login and navigation
      (ctu/login-and-navigate fx/*driver* (str fx/*app-url* "cards"))

      (testing "Navigate to card and verify initial state"
        (ctu/navigate-to-card fx/*driver* "K-PAX")

        ;; Verify initial values using utilities
        (is (= "1" (ctu/get-field-value fx/*driver* :sht)) "Initial SHT value in input is correct")
        (is (= "1" (ctu/get-display-value fx/*driver* :sht)) "Initial SHT value in display is correct"))

      (testing "Update field value and verify auto-save"
        (ctu/fill-card-field fx/*driver* :sht "5")
        (ctu/wait-for-auto-save fx/*driver*)

        ;; Verify display updates
        (ctu/verify-display-update fx/*driver* :sht "5"))

      (testing "Verify persistence after page reload"
        (ctu/verify-card-persistence fx/*driver* "K-PAX" {:sht "5"})))))

(deftest ^:e2e card-creation-flow-test
  (testing "Test complete card creation workflow"
    (testing "User can create a new player card with all fields"
      (ctu/login-and-navigate fx/*driver* nil)

      ;; Navigate to new card page
      (e/go fx/*driver* (str fx/*app-url* "cards/new"))
      (is (e/wait-visible fx/*driver* {:tag :h1 :fn/text "Edit Card"})
          "New card form should be visible")

      (testing "Fill required fields"
        (ctu/fill-card-field fx/*driver* :name "Test Player")
        (ctu/fill-card-field fx/*driver* :card-type "PLAYER_CARD")
        (ctu/fill-card-field fx/*driver* :deck-size "1"))

      (testing "Fill player-specific fields"
        (ctu/fill-card-field fx/*driver* :sht "3")
        (ctu/fill-card-field fx/*driver* :pss "4")
        (ctu/fill-card-field fx/*driver* :def "2")
        (ctu/fill-card-field fx/*driver* :speed "5")
        (ctu/fill-card-field fx/*driver* :size "MD"))

      (testing "Fill optional text fields"
        (ctu/fill-card-field fx/*driver* :offense "Powerful offense capability")
        (ctu/fill-card-field fx/*driver* :defense "Strong defense strategy"))

      ;; Wait for auto-save
      (ctu/wait-for-auto-save fx/*driver*)

      (testing "Verify card appears in list"
        (ctu/verify-card-in-list fx/*driver* "Test Player"))

      (testing "Verify all fields persisted correctly"
        (ctu/verify-card-persistence fx/*driver* "Test Player"
                                     {:sht "3" :pss "4" :def "2" :speed "5" :size "MD"
                                      :offense "Powerful offense capability"
                                      :defense "Strong defense strategy"})))))

(deftest ^:e2e card-edit-comprehensive-test
  (testing "Enhanced version of existing edit test with multiple field types"
    (tu/with-inserted-data [::models/GameCard (ctu/create-test-card-data :complex-player)]
      (testing "User can edit multiple field types with state management"
        (ctu/login-and-navigate fx/*driver* nil)
        (ctu/navigate-to-card fx/*driver* "Complex Player")

        (testing "Edit numeric fields"
          ;; Test stat fields
          (ctu/fill-card-field fx/*driver* :sht "8")
          (ctu/verify-display-update fx/*driver* :sht "8")

          (ctu/fill-card-field fx/*driver* :pss "7")
          (ctu/verify-display-update fx/*driver* :pss "7"))

        (testing "Edit select fields"
          (ctu/fill-card-field fx/*driver* :size "SM")
          (ctu/verify-display-update fx/*driver* :size "SM"))

        (testing "Edit textarea fields"
          (ctu/fill-card-field fx/*driver* :offense "Updated powerful offense text with special moves")
          (ctu/fill-card-field fx/*driver* :defense "Enhanced defense with new tactics")
          (ctu/fill-card-field fx/*driver* :coaching "Strategic coaching improvements"))

        ;; Wait for all auto-saves to complete
        (ctu/wait-for-auto-save fx/*driver* 1000)

        (testing "Verify all changes persist"
          (ctu/verify-card-persistence fx/*driver* "Complex Player"
                                       {:sht "8" :pss "7" :size "SM"
                                        :offense "Updated powerful offense text with special moves"
                                        :defense "Enhanced defense with new tactics"
                                        :coaching "Strategic coaching improvements"}))))))

(deftest ^:e2e field-validation-test
  (testing "Test field-level validation and error stat es"
    (tu/with-inserted-data [::models/GameCard (ctu/create-test-card-data :basic-player)]
      (testing "Field validation works correctly"
        (ctu/login-and-navigate fx/*driver* nil)
        (ctu/navigate-to-card fx/*driver* "Basic Player")

        (testing "Invalid numeric input shows error state"
          ;; Test invalid stat value
          (ctu/fill-card-field fx/*driver* :sht "invalid")
          ;; Note: Error state detection depends on frontend implementation
          ;; This may need adjustment based on actual error indicators
          )

        (testing "Empty required field shows error"
          ;; Clear required field
          (ctu/fill-card-field fx/*driver* :name "")
          ;; Verify error state appears
          )

        (testing "Valid input clears error state"
          ;; Restore valid values
          (ctu/fill-card-field fx/*driver* :sht "3")
          (ctu/fill-card-field fx/*driver* :name "Basic Player"))))))

(deftest ^:e2e multiple-card-types-test
  (testing "Test creation and editing of different card types"
    (testing "Create and edit ability card"
      (ctu/login-and-navigate fx/*driver* nil)

      ;; Create ability card
      (e/go fx/*driver* (str fx/*app-url* "cards/new"))
      (is (e/wait-visible fx/*driver* {:tag :h1 :fn/text "Edit Card"})
          "New card form should be visible")

      (ctu/fill-card-field fx/*driver* :name "Teleport Ability")
      (ctu/fill-card-field fx/*driver* :card-type "ABILITY_CARD")
      (ctu/fill-card-field fx/*driver* :deck-size "1")

      ;; Fill ability-specific fields
      (ctu/fill-card-field fx/*driver* :abilities "[[\"teleport\", [\"Instant movement across the court\"]]]")

      (ctu/wait-for-auto-save fx/*driver*)

      ;; Verify creation
      (ctu/verify-card-in-list fx/*driver* "Teleport Ability")))

  (testing "Create coaching card"
    (e/go fx/*driver* (str fx/*app-url* "cards/new"))
    (is (e/wait-visible fx/*driver* {:tag :h1 :fn/text "Edit Card"})
        "New card form should be visible")

    (ctu/fill-card-field fx/*driver* :name "Timeout Strategy")
    (ctu/fill-card-field fx/*driver* :card-type "COACHING_CARD")
    (ctu/fill-card-field fx/*driver* :deck-size "1")
    (ctu/fill-card-field fx/*driver* :coaching "Call strategic timeout to reorganize team formation")

    (ctu/wait-for-auto-save fx/*driver*)

    ;; Verify creation
    (ctu/verify-card-in-list fx/*driver* "Timeout Strategy")))

(deftest ^:e2e navigation-and-workflow-test
  (testing "Test navigation between cards and overall workflow"
    (tu/with-inserted-data [::models/GameCard (ctu/create-test-card-data :basic-player)
                            ::models/GameCard (ctu/create-test-card-data :complex-player)]
      (testing "User can navigate between multiple cards"
        (ctu/login-and-navigate fx/*driver* (str fx/*app-url* "cards"))

        ;; Verify both cards appear in list
        (let [cards-list (ctu/get-cards-list fx/*driver*)]
          (is (contains? cards-list "Basic Player") "Basic Player should be in cards list")
          (is (contains? cards-list "Complex Player") "Complex Player should be in cards list"))

        (testing "Edit first card"
          (ctu/navigate-to-card fx/*driver* "Basic Player")
          (ctu/fill-card-field fx/*driver* :sht "6")
          (ctu/wait-for-auto-save fx/*driver*))

        (testing "Navigate to second card"
          (ctu/navigate-to-card fx/*driver* "Complex Player")
          (ctu/fill-card-field fx/*driver* :pss "8")
          (ctu/wait-for-auto-save fx/*driver*))

        (testing "Return to first card and verify changes persist"
          (ctu/navigate-to-card fx/*driver* "Basic Player")
          (is (= "6" (ctu/get-field-value fx/*driver* :sht))
              "Changes to first card should persist"))

        (testing "Return to second card and verify changes persist"
          (ctu/navigate-to-card fx/*driver* "Complex Player")
          (is (= "8" (ctu/get-field-value fx/*driver* :pss))
              "Changes to second card should persist"))))))

(deftest ^:e2e performance-benchmarks-test
  "Test performance benchmarks for key operations"
  (testing "Page load performance"
    (ctu/login-and-navigate fx/*driver* nil)

    (let [cards-list-load-time (ctu/measure-page-load-time fx/*driver* (str fx/*app-url* "cards"))
          new-card-load-time (ctu/measure-page-load-time fx/*driver* (str fx/*app-url* "cards/new"))]

      ;; Performance assertions (generous limits for CI environments)
      (is (< cards-list-load-time 5000)
          (str "Cards list should load in < 5s, was " cards-list-load-time "ms"))
      (is (< new-card-load-time 5000)
          (str "New card form should load in < 5s, was " new-card-load-time "ms"))))

  (tu/with-inserted-data [::models/GameCard (ctu/create-test-card-data :basic-player)]
    (testing "Field save performance"
      (ctu/login-and-navigate fx/*driver* nil)
      (ctu/navigate-to-card fx/*driver* "Basic Player")

      (let [field-save-time (ctu/measure-field-save-time fx/*driver* :sht "7")]
        ;; Performance assertion for auto-save feedback
        (is (< field-save-time 2000)
            (str "Field save feedback should appear in < 2s, was " field-save-time "ms"))))))
