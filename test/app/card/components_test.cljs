(ns app.card.components-test
  "Comprehensive tests for card components"
  (:require
   [app.card.components :as components]
   [app.card.state :as card.state]
   [app.test-utils :as test-utils]
   [app.test-utils.state :as state-utils]
   [cljs.test :as t :include-macros true]
   [uix.core :refer [$]]

   ["@testing-library/react" :as tlr]
   ["@testing-library/user-event" :as user-event]
   ))

;; Setup test environment
(test-utils/setup-frontend-test-env!)
(t/use-fixtures :each test-utils/react-cleanup-fixture)

;; ============================================================================
;; Basic Component Tests (No Apollo/State Context Required)
;; ============================================================================

(t/deftest card-label-with-loading-test
  (t/testing "renders display label with loading indicator"
    (let [^js result (test-utils/render
                      ($ :div {}
                         ($ :label {:class "block text-sm font-medium text-gray-700 mb-1"}
                            "Test Label Loading"
                            ($ :span {:class "text-purple-600 ml-2"} "💾"))))]
      (t/is (not (nil? (.queryByText result "Test Label Loading"))))
      (t/is (not (nil? (.queryByText result "💾")))))))

(t/deftest card-label-without-loading-test
  (t/testing "renders display label without loading indicator"
    (let [^js result (test-utils/render
                      ($ :div {}
                         ($ :label {:class "block text-sm font-medium text-gray-700 mb-1"}
                            "Test Label No Loading")))]
      (t/is (not (nil? (.queryByText result "Test Label No Loading"))))
      (t/is (nil? (.queryByText result "💾"))))))

(t/deftest card-status-dirty-test
  (t/testing "shows modified indicator when dirty"
    (let [^js result (test-utils/render ($ components/card-status {:dirty? true}))]
      (t/is (not (nil? (.queryByText result "📝 Modified")))))))

(t/deftest card-status-clean-test
  (t/testing "no modified indicator when not dirty"
    (let [^js result (test-utils/render ($ components/card-status {:dirty? false}))]
      (t/is (nil? (.queryByText result "📝 Modified"))))))

(t/deftest multi-text-renders-inputs-test
  (t/testing "renders multiple text inputs"
    (let [^js result (test-utils/render ($ components/multi-text {:value ["Item 1" "Item 2"]
                                                                  :update-value identity}))]
      (t/is (not (nil? (.queryByDisplayValue result "Item 1"))))
      (t/is (not (nil? (.queryByDisplayValue result "Item 2")))))))

(t/deftest multi-text-buttons-test
  (t/testing "renders correct number of buttons"
    (let [^js result (test-utils/render ($ components/multi-text {:value ["Item 1" "Item 2"]
                                                                  :update-value identity}))
          add-buttons (.queryAllByText result "+")
          remove-buttons (.queryAllByText result "-")]
      ;; Should have one + button and two - buttons (one per item)
      (t/is (= 1 (.-length add-buttons)) "Should have exactly one add button")
      (t/is (= 2 (.-length remove-buttons)) "Should have one remove button per item"))))

(t/deftest multi-text-empty-state-test
  (t/testing "renders empty state correctly"
    (let [^js result (test-utils/render ($ components/multi-text {:value []
                                                                  :update-value identity}))
          add-buttons (.queryAllByText result "+")
          remove-buttons (.queryAllByText result "-")]
      ;; Should have one + button and no - buttons
      (t/is (= 1 (.-length add-buttons)) "Should have exactly one add button")
      (t/is (= 0 (.-length remove-buttons)) "Should have no remove buttons when empty"))))

;; ============================================================================
;; Card Input Component Tests
;; ============================================================================

(t/deftest card-input-renders-text-input-test
  (t/testing "renders text input"
    (let [^js result (test-utils/render ($ components/card-input {:field-key :name
                                                                  :input-type "text"
                                                                  :value "Test Value"
                                                                  :update-value identity
                                                                  :display-label "Name"}))]
      (t/is (not (nil? (.queryByDisplayValue result "Test Value")))))))

(t/deftest card-input-renders-textarea-test
  (t/testing "renders textarea"
    (let [^js result (test-utils/render ($ components/card-input {:field-key :description
                                                                  :input-type "textarea"
                                                                  :value "Test Description"
                                                                  :update-value identity
                                                                  :display-label "Description"}))]
      (t/is (not (nil? (.queryByDisplayValue result "Test Description")))))))

(t/deftest card-input-renders-select-dropdown-test
  (t/testing "renders select dropdown"
    (let [^js result (test-utils/render ($ components/card-input {:field-key :size
                                                                  :input-type "select"
                                                                  :value :SM
                                                                  :update-value identity
                                                                  :display-label "Size"
                                                                  :options {:SM "Small" :MD "Medium" :LG "Large"}}))]
      (t/is (not (nil? (.queryByRole result "combobox"))))
      (t/is (not (nil? (.queryByText result "Small"))))
      (t/is (not (nil? (.queryByText result "Medium"))))
      (t/is (not (nil? (.queryByText result "Large")))))))

(t/deftest card-input-renders-multitext-input-test
  (t/testing "renders multitext input"
    (let [^js result (test-utils/render ($ components/card-input {:field-key :abilities
                                                                  :input-type "multitext"
                                                                  :value ["Ability 1" "Ability 2"]
                                                                  :update-value identity
                                                                  :display-label "Abilities"}))]
      (t/is (not (nil? (.queryByDisplayValue result "Ability 1"))))
      (t/is (not (nil? (.queryByDisplayValue result "Ability 2")))))))

(t/deftest card-input-handles-disabled-state-test
  (t/testing "handles disabled state"
    (let [^js result (test-utils/render ($ components/card-input {:field-key :name
                                                                  :input-type "text"
                                                                  :value "Test"
                                                                  :disabled true
                                                                  :update-value identity
                                                                  :display-label "Name"}))
          input (.queryByDisplayValue result "Test")]
      (t/is (not (nil? input)))
      (t/is (.-disabled input)))))

;; ============================================================================
;; Card Field Component Tests (with State Management Context)
;; ============================================================================

(t/deftest card-field-renders-with-state-management-test
  (t/testing "renders with state management"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/card-field {:field-key :name :label "Card Name"})))]
      (t/is (not (nil? (.queryByLabelText result "Card Name")))))))

(t/deftest card-field-determines-input-type-for-number-fields-test
  (t/testing "determines input type for number fields"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/card-field {:field-key :sht})))
          input (.queryByLabelText result "Sht")]
      (t/is (not (nil? input)))
      (t/is (= "number" (.-type input))))))

(t/deftest card-field-determines-input-type-for-textarea-test
  (t/testing "determines input type for textarea for abilities"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/card-field {:field-key :abilities})))
          textarea (.queryByLabelText result "Abilities")]
      (t/is (not (nil? textarea)))
      (t/is (= "TEXTAREA" (.-tagName textarea))))))

(t/deftest card-field-determines-input-type-for-text-test
  (t/testing "determines input type for text input as default"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/card-field {:field-key :name})))
          input (.queryByLabelText result "Name")]
      (t/is (not (nil? input)))
      (t/is (= "text" (.-type input))))))

(t/deftest card-field-applies-proper-styling-classes-test
  (t/testing "applies proper styling classes"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/card-field {:field-key :name})))
          input (.queryByLabelText result "Name")]
      (t/is (not (nil? input)))
      (t/is (.includes (.-className input) "border")))))

;; ============================================================================
;; Card Editor Component Tests
;; ============================================================================

(t/deftest player-card-editor-renders-all-fields-test
  (t/testing "renders all player card fields"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/player-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Shot"))))
      (t/is (not (nil? (.queryByLabelText result "Pass"))))
      (t/is (not (nil? (.queryByLabelText result "Defense"))))
      (t/is (not (nil? (.queryByLabelText result "Speed"))))
      (t/is (not (nil? (.queryByLabelText result "Size"))))
      (t/is (not (nil? (.queryByText result "Abilities")))))))

(t/deftest player-card-editor-stat-fields-are-number-inputs-test
  (t/testing "stat fields are number inputs"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/player-card-editor {})))]
      (t/is (= "number" (.-type (.queryByLabelText result "Shot"))))
      (t/is (= "number" (.-type (.queryByLabelText result "Pass"))))
      (t/is (= "number" (.-type (.queryByLabelText result "Defense"))))
      (t/is (= "number" (.-type (.queryByLabelText result "Speed")))))))

(t/deftest player-card-editor-size-field-is-select-test
  (t/testing "size field is select dropdown"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/player-card-editor {})))]
      (t/is (= "SELECT" (.-tagName (.queryByLabelText result "Size")))))))

(t/deftest ability-card-editor-renders-fields-test
  (t/testing "renders ability card specific fields"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/ability-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByText result "Abilities")))))))

(t/deftest ability-card-editor-fate-is-number-input-test
  (t/testing "fate is number input"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/ability-card-editor {})))]
      (t/is (= "number" (.-type (.queryByLabelText result "Fate")))))))

(t/deftest play-card-editor-test
  (t/testing "renders play card specific fields"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/play-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByText result "Abilities")))))))

(t/deftest split-play-card-editor-renders-fields-test
  (t/testing "renders split play card specific fields"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/split-play-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByLabelText result "Offense"))))
      (t/is (not (nil? (.queryByText result "Abilities")))))))

(t/deftest split-play-card-editor-offense-is-textarea-test
  (t/testing "offense is textarea"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/split-play-card-editor {})))]
      (t/is (= "TEXTAREA" (.-tagName (.queryByLabelText result "Offense")))))))

(t/deftest coaching-card-editor-renders-fields-test
  (t/testing "renders coaching card specific fields"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/coaching-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByLabelText result "Coaching")))))))

(t/deftest coaching-card-editor-coaching-field-is-textarea-test
  (t/testing "coaching is textarea"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/coaching-card-editor {})))]
      (t/is (= "TEXTAREA" (.-tagName (.queryByLabelText result "Coaching")))))))

(t/deftest standard-action-card-editor-test
  (t/testing "renders standard action card specific fields"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/standard-action-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByText result "Abilities")))))))

(t/deftest team-asset-card-editor-renders-fields-test
  (t/testing "renders team asset card specific fields"
    (let [^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/team-asset-card-editor {})))]
      (t/is (not (nil? (.queryByLabelText result "Fate"))))
      (t/is (not (nil? (.queryByLabelText result "Asset Power")))))))

(t/deftest team-asset-card-editor-asset-power-is-textarea-test
  (t/testing "asset power is textarea"
    (let [^js result (test-utils/render-with-apollo
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

(defn ue [] (user-event/userEvent.setup #js {:document js/document}))

(t/deftest multi-text-add-item-test
  (t/testing "adds new item when + button clicked"
    (let [update-calls (atom [])
          result (test-utils/render ($ components/multi-text {:value ["Item 1"]
                                                              :update-value #(swap! update-calls conj %)}))]
      (js/console.log result)
      (js/console.log (.getByText result "+"))
      (.click (ue) (.getByText result "+"))
      (t/is (= [["Item 1" ""]] @update-calls)))))

(t/deftest multi-text-remove-item-test
  (t/testing "removes item when - button clicked"
    (let [update-calls (atom [])
          result (test-utils/render ($ components/multi-text {:value ["Item 1" "Item 2"]
                                                              :update-value #(swap! update-calls conj %)}))]
      ;; Click the first remove button
      (tlr/fireEvent.click (first (.getAllByText result "-")))
      (t/is (= [["Item 2"]] @update-calls)))))

(t/deftest multi-text-update-item-test
  (t/testing "updates item value on text change"
    (let [update-calls (atom [])
          result (test-utils/render ($ components/multi-text {:value ["Unique Item"]
                                                              :update-value #(swap! update-calls conj %)}))
          textarea (.getByDisplayValue result "Unique Item")]
      (user-event/userEvent.type textarea "Updated Unique Item")
      (t/is (= [["Updated Unique Item"]] @update-calls)))))

(t/deftest card-input-text-interaction-test
  (t/testing "text input calls update-value on change"
    (let [update-calls (atom [])
          result (test-utils/render ($ components/card-input {:field-key :name
                                                              :input-type "text"
                                                              :value "Initial"
                                                              :update-value #(swap! update-calls conj %)}))
          ^js input (.getByDisplayValue result "Initial")]
      (user-event/userEvent.type input "Updated")
      (t/is (= ["Updated"] @update-calls)))))

(t/deftest card-input-number-interaction-test
  (t/testing "number input converts string to number"
    (let [update-calls (atom [])
          result (test-utils/render ($ components/card-input {:field-key :sht
                                                              :input-type "number"
                                                              :value 5
                                                              :update-value #(swap! update-calls conj %)}))
          ^js input (.getByDisplayValue result "5")]
      (tlr/fireEvent.change input #js {:target #js {:value "10"}})
      (t/is (= [10] @update-calls)))))

(t/deftest card-input-select-interaction-test
  (t/testing "select calls update-value with correct keyword"
    (let [update-calls (atom [])
          result (test-utils/render ($ components/card-input {:field-key :size
                                                              :input-type "select"
                                                              :value :SM
                                                              :options {:SM "Small" :MD "Medium" :LG "Large"}
                                                              :update-value #(swap! update-calls conj %)}))
          select (.getByRole result "combobox")]
      (tlr/fireEvent.change select #js {:target #js {:value ":MD"}})
      (t/is (= [:MD] @update-calls)))))

;; ============================================================================
;; Card Field Integration Tests with State
;; ============================================================================

(t/deftest card-field-state-integration-test
  (t/testing "card field integrates with state management"
    (let [spy (test-utils/with-card-operation-spy)
          ^js result (test-utils/render-with-apollo
                      ($ card.state/with-card {:new? true}
                         ($ components/card-field {:field-key :name})))
          ^js input (.queryByLabelText result "Name")]

      ;; Find the input field
      (t/is (not (nil? input)))

      ;; Type into the field
      (tlr/fireEvent.change input #js {:target #js {:value "Test Card Name"}})

      ;; Verify the field shows the new value
      (t/is (= "Test Card Name" (.-value input))))))
