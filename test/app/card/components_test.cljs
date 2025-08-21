(ns app.card.components-test
  "Comprehensive tests for card components"
  (:require
   [app.card.components :as components]
   [app.card.state :as card.state]
   [app.test-utils :as test-utils]
   [app.test-utils.state :as state-utils]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]

   ["@testing-library/react" :as tlr]))

;; Setup test environment
(test-utils/setup-frontend-test-env!)
(t/use-fixtures :each test-utils/react-cleanup-fixture)

;; ============================================================================
;; Basic Component Tests (No Apollo/State Context Required)
;; ============================================================================

(t/deftest card-label-with-loading-test
  (t/testing "renders display label with loading indicator"
    (let [result (tlr/render
                  ($ :div {}
                     ($ :label {:class "block text-sm font-medium text-gray-700 mb-1"}
                        "Test Label Loading"
                        ($ :span {:class "text-purple-600 ml-2"} "💾"))))]
      (t/is (not (nil? (.queryByText result "Test Label Loading"))))
      (t/is (not (nil? (.queryByText result "💾")))))))

(t/deftest card-label-without-loading-test
  (t/testing "renders display label without loading indicator"
    (let [result (tlr/render
                  ($ :div {}
                     ($ :label {:class "block text-sm font-medium text-gray-700 mb-1"}
                        "Test Label No Loading")))]
      (t/is (not (nil? (.queryByText result "Test Label No Loading"))))
      (t/is (nil? (.queryByText result "💾"))))))

(t/deftest card-status-dirty-test
  (t/testing "shows modified indicator when dirty"
    (let [result (tlr/render ($ components/card-status {:dirty? true}))]
      (t/is (not (nil? (.queryByText result "📝 Modified")))))))

(t/deftest card-status-clean-test
  (t/testing "no modified indicator when not dirty"
    (let [result (tlr/render ($ components/card-status {:dirty? false}))]
      (t/is (nil? (.queryByText result "📝 Modified"))))))

(t/deftest multi-text-renders-inputs-test
  (t/testing "renders multiple text inputs"
    (let [result (tlr/render ($ components/multi-text {:value ["Item 1" "Item 2"]
                                                       :update-value identity}))]
      (t/is (not (nil? (.queryByDisplayValue result "Item 1"))))
      (t/is (not (nil? (.queryByDisplayValue result "Item 2")))))))

(t/deftest multi-text-buttons-test
  (t/testing "renders correct number of buttons"
    (let [result (tlr/render ($ components/multi-text {:value ["Item 1" "Item 2"]
                                                       :update-value identity}))]
      ;; Should have one + button and two - buttons (one per item)
      (let [add-buttons (.queryAllByText result "+")
            remove-buttons (.queryAllByText result "-")]
        (t/is (= 1 (.-length add-buttons)) "Should have exactly one add button")
        (t/is (= 2 (.-length remove-buttons)) "Should have one remove button per item")))))

(t/deftest multi-text-empty-state-test
  (t/testing "renders empty state correctly"
    (let [result (tlr/render ($ components/multi-text {:value []
                                                       :update-value identity}))]
      ;; Should have one + button and no - buttons
      (let [add-buttons (.queryAllByText result "+")
            remove-buttons (.queryAllByText result "-")]
        (t/is (= 1 (.-length add-buttons)) "Should have exactly one add button")
        (t/is (= 0 (.-length remove-buttons)) "Should have no remove buttons when empty")))))

;; ============================================================================
;; Card Input Component Tests
;; ============================================================================

(t/deftest card-input-test
  (t/testing "renders text input"
    (let [result (tlr/render ($ components/card-input {:field-key :name
                                                       :input-type "text"
                                                       :value "Test Value"
                                                       :update-value identity
                                                       :display-label "Name"}))]
      (t/is (not (nil? (.queryByDisplayValue result "Test Value"))))))

  (t/testing "renders textarea"
    (let [result (tlr/render ($ components/card-input {:field-key :description
                                                       :input-type "textarea"
                                                       :value "Test Description"
                                                       :update-value identity
                                                       :display-label "Description"}))]
      (t/is (not (nil? (.queryByDisplayValue result "Test Description"))))))

  (t/testing "renders select dropdown"
    (let [result (tlr/render ($ components/card-input {:field-key :size
                                                       :input-type "select"
                                                       :value :SM
                                                       :update-value identity
                                                       :display-label "Size"
                                                       :options {:SM "Small" :MD "Medium" :LG "Large"}}))]
      (t/is (not (nil? (.queryByRole result "combobox"))))
      (t/is (not (nil? (.queryByText result "Small"))))
      (t/is (not (nil? (.queryByText result "Medium"))))
      (t/is (not (nil? (.queryByText result "Large"))))))

  (t/testing "renders multitext input"
    (let [result (tlr/render ($ components/card-input {:field-key :abilities
                                                       :input-type "multitext"
                                                       :value ["Ability 1" "Ability 2"]
                                                       :update-value identity
                                                       :display-label "Abilities"}))]
      (t/is (not (nil? (.queryByDisplayValue result "Ability 1"))))
      (t/is (not (nil? (.queryByDisplayValue result "Ability 2"))))))

  (t/testing "handles disabled state"
    (let [result (tlr/render ($ components/card-input {:field-key :name
                                                       :input-type "text"
                                                       :value "Test"
                                                       :disabled true
                                                       :update-value identity
                                                       :display-label "Name"}))]
      (let [input (.queryByDisplayValue result "Test")]
        (t/is (not (nil? input)))
        (t/is (.-disabled input))))))

;; ============================================================================
;; Card Field Component Tests (with State Management Context)
;; ============================================================================

(t/deftest card-field-test
  (t/testing "renders with state management"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/card-field {:field-key :name :label "Card Name"})))]
      (t/is (not (nil? (.queryByLabelText result "Card Name"))))))

  (t/testing "determines input type based on field key"
    (t/testing "number fields"
      (let [result (test-utils/render-with-apollo
                    ($ card.state/with-card {:new? true}
                       ($ components/card-field {:field-key :sht})))]
        (let [input (.queryByLabelText result "Sht")]
          (t/is (not (nil? input)))
          (t/is (= "number" (.-type input))))))

    (t/testing "textarea for abilities"
      (let [result (test-utils/render-with-apollo
                    ($ card.state/with-card {:new? true}
                       ($ components/card-field {:field-key :abilities})))]
        (let [textarea (.queryByLabelText result "Abilities")]
          (t/is (not (nil? textarea)))
          (t/is (= "TEXTAREA" (.-tagName textarea))))))

    (t/testing "text input as default"
      (let [result (test-utils/render-with-apollo
                    ($ card.state/with-card {:new? true}
                       ($ components/card-field {:field-key :name})))]
        (let [input (.queryByLabelText result "Name")]
          (t/is (not (nil? input)))
          (t/is (= "text" (.-type input)))))))

  (t/testing "applies proper styling classes"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/card-field {:field-key :name})))]
      (let [input (.queryByLabelText result "Name")]
        (t/is (not (nil? input)))
        (t/is (.includes (.-className input) "border"))))))

;; ============================================================================
;; Card Editor Component Tests
;; ============================================================================

(t/deftest player-card-editor-test
  (t/testing "renders all player card fields"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/player-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Shot"))))
      (t/is (not (nil? (.queryByLabelText result "Pass"))))
      (t/is (not (nil? (.queryByLabelText result "Defense"))))
      (t/is (not (nil? (.queryByLabelText result "Speed"))))
      (t/is (not (nil? (.queryByLabelText result "Size"))))
      (t/is (not (nil? (.queryByLabelText result "Abilities"))))))

  (t/testing "stat fields are number inputs"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/player-card-editor {})))]
      (t/is (= "number" (.-type (.queryByLabelText result "Shot"))))
      (t/is (= "number" (.-type (.queryByLabelText result "Pass"))))
      (t/is (= "number" (.-type (.queryByLabelText result "Defense"))))
      (t/is (= "number" (.-type (.queryByLabelText result "Speed"))))))

  (t/testing "size field is select dropdown"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/player-card-editor {})))]
      (t/is (= "SELECT" (.-tagName (.queryByLabelText result "Size")))))))

(t/deftest ability-card-editor-test
  (t/testing "renders ability card specific fields"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/ability-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByLabelText result "Abilities"))))))

  (t/testing "fate is number input"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/ability-card-editor {})))]
      (t/is (= "number" (.-type (.queryByLabelText result "Fate")))))))

(t/deftest play-card-editor-test
  (t/testing "renders play card specific fields"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/play-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByLabelText result "Abilities")))))))

(t/deftest split-play-card-editor-test
  (t/testing "renders split play card specific fields"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/split-play-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByLabelText result "Offense"))))
      (t/is (not (nil? (.queryByLabelText result "Abilities"))))))

  (t/testing "offense is textarea"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/split-play-card-editor {})))]
      (t/is (= "TEXTAREA" (.-tagName (.queryByLabelText result "Offense")))))))

(t/deftest coaching-card-editor-test
  (t/testing "renders coaching card specific fields"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/coaching-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByLabelText result "Coaching"))))))

  (t/testing "coaching is textarea"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/coaching-card-editor {})))]
      (t/is (= "TEXTAREA" (.-tagName (.queryByLabelText result "Coaching")))))))

(t/deftest standard-action-card-editor-test
  (t/testing "renders standard action card specific fields"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/standard-action-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByLabelText result "Abilities")))))))

(t/deftest team-asset-card-editor-test
  (t/testing "renders team asset card specific fields"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/team-asset-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByLabelText result "Asset Power"))))))

  (t/testing "asset power is textarea"
    (let [result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/team-asset-card-editor {})))]
      (t/is (= "TEXTAREA" (.-tagName (.queryByLabelText result "Asset Power")))))))

;; ============================================================================
;; Component Registry Tests
;; ============================================================================

(t/deftest card-type-components-test
  (t/testing "registry contains all card types"
    (t/is (contains? components/card-type-components :card-type-enum/PLAYER_CARD))
    (t/is (contains? components/card-type-components :card-type-enum/ABILITY_CARD))
    (t/is (contains? components/card-type-components :card-type-enum/PLAY_CARD))
    (t/is (contains? components/card-type-components :card-type-enum/SPLIT_PLAY_CARD))
    (t/is (contains? components/card-type-components :card-type-enum/COACHING_CARD))
    (t/is (contains? components/card-type-components :card-type-enum/STANDARD_ACTION_CARD))
    (t/is (contains? components/card-type-components :card-type-enum/TEAM_ASSET_CARD)))

  (t/testing "registry maps to correct components"
    (t/is (= components/player-card-editor
             (get components/card-type-components :card-type-enum/PLAYER_CARD)))
    (t/is (= components/ability-card-editor
             (get components/card-type-components :card-type-enum/ABILITY_CARD)))
    (t/is (= components/play-card-editor
             (get components/card-type-components :card-type-enum/PLAY_CARD)))
    (t/is (= components/split-play-card-editor
             (get components/card-type-components :card-type-enum/SPLIT_PLAY_CARD)))
    (t/is (= components/coaching-card-editor
             (get components/card-type-components :card-type-enum/COACHING_CARD)))
    (t/is (= components/standard-action-card-editor
             (get components/card-type-components :card-type-enum/STANDARD_ACTION_CARD)))
    (t/is (= components/team-asset-card-editor
             (get components/card-type-components :card-type-enum/TEAM_ASSET_CARD)))))

;; ============================================================================
;; Integration Tests with Interactions
;; ============================================================================

(t/deftest multi-text-interactions-test
  (t/testing "adds new item when + button clicked"
    (let [update-calls (atom [])
          result (tlr/render ($ components/multi-text {:value ["Item 1"]
                                                       :update-value #(swap! update-calls conj %)}))]
      (tlr/fireEvent.click (.getByText result "+"))
      (t/is (= [["Item 1" ""]] @update-calls))))

  (t/testing "removes item when - button clicked"
    (let [update-calls (atom [])
          result (tlr/render ($ components/multi-text {:value ["Item 1" "Item 2"]
                                                       :update-value #(swap! update-calls conj %)}))]
      ;; Click the first remove button
      (tlr/fireEvent.click (first (.getAllByText result "-")))
      (t/is (= [["Item 2"]] @update-calls))))

  (t/testing "updates item value on text change"
    (let [update-calls (atom [])
          result (tlr/render ($ components/multi-text {:value ["Unique Item"]
                                                       :update-value #(swap! update-calls conj %)}))]
      (let [textarea (.getByDisplayValue result "Unique Item")]
        (tlr/fireEvent.change textarea #js {:target #js {:value "Updated Unique Item"}}))
      (t/is (= [["Updated Unique Item"]] @update-calls)))))

(t/deftest card-input-interactions-test
  (t/testing "text input calls update-value on change"
    (let [update-calls (atom [])
          result (tlr/render ($ components/card-input {:field-key :name
                                                       :input-type "text"
                                                       :value "Initial"
                                                       :update-value #(swap! update-calls conj %)}))]
      (let [input (.getByDisplayValue result "Initial")]
        (tlr/fireEvent.change input #js {:target #js {:value "Updated"}}))
      (t/is (= ["Updated"] @update-calls))))

  (t/testing "number input converts string to number"
    (let [update-calls (atom [])
          result (tlr/render ($ components/card-input {:field-key :sht
                                                       :input-type "number"
                                                       :value 5
                                                       :update-value #(swap! update-calls conj %)}))]
      (let [input (.getByDisplayValue result "5")]
        (tlr/fireEvent.change input #js {:target #js {:value "10"}}))
      (t/is (= [10] @update-calls))))

  (t/testing "select calls update-value with correct keyword"
    (let [update-calls (atom [])
          result (tlr/render ($ components/card-input {:field-key :size
                                                       :input-type "select"
                                                       :value :SM
                                                       :options {:SM "Small" :MD "Medium" :LG "Large"}
                                                       :update-value #(swap! update-calls conj %)}))]
      (let [select (.getByRole result "combobox")]
        (tlr/fireEvent.change select #js {:target #js {:value ":MD"}}))
      (t/is (= [:MD] @update-calls)))))

;; ============================================================================
;; Card Field Integration Tests with State
;; ============================================================================

(t/deftest card-field-state-integration-test
  (t/testing "card field integrates with state management"
    (let [spy (test-utils/with-card-operation-spy)
          result (test-utils/render-with-apollo
                  ($ card.state/with-card {:new? true}
                     ($ components/card-field {:field-key :name})))]

      ;; Find the input field
      (let [input (.queryByLabelText result "Name")]
        (t/is (not (nil? input)))

        ;; Type into the field
        (tlr/fireEvent.change input #js {:target #js {:value "Test Card Name"}})

        ;; Verify the field shows the new value
        (t/is (= "Test Card Name" (.-value input)))))))
