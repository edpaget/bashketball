(ns app.card.components
  (:require
   ["@headlessui/react" :as headless]
   [app.card.state :as card.state]
   [clojure.string :as str]
   [uix.core :as uix :refer [defui $]]))

(defui card-label
  [{:keys [display-label is-loading? is-validating?]}]
  ($ headless/Label {:class "block text-sm font-medium text-gray-700 mb-1"}
     display-label
     (when is-loading? ($ :span {:class "text-purple-600 ml-2"} "💾"))
     (when is-validating? ($ :span {:class "text-yellow-600 ml-2"} "🔍"))))

(defui card-status
  [{:keys [is-dirty? is-validating? is-loading?]}]
  ($ :div {:class "mt-1 flex items-center text-xs space-x-2"}
     (when is-dirty?
       ($ :span {:class "text-blue-600"} "📝 Modified"))
     (when is-validating?
       ($ :span {:class "text-yellow-600"} "🔍 Validating..."))
     (when is-loading?
       ($ :span {:class "text-purple-600"} "💾 Saving..."))))

(defui card-conflict
  [{:keys [value resolve-conflict-with-local conflict-value resolve-conflict-with-server]}]
  ($ :div {:class "mt-2 p-3 bg-orange-50 border border-orange-200 rounded-md"}
     ($ :p {:class "text-sm font-medium text-orange-800 mb-2"}
        "⚠️ Conflict Detected")
     ($ :p {:class "text-xs text-orange-700 mb-3"}
        "Your changes conflict with server updates:")

     ($ :div {:class "space-y-2"}
        ($ :div {:class "flex items-center justify-between p-2 bg-white rounded border"}
           ($ :div
              ($ :p {:class "text-sm font-medium"} "Your Version:")
              ($ :p {:class "text-xs text-gray-600"} (str value)))
           $ headless/Button {:class "px-3 py-1 bg-blue-500 text-white rounded text-xs hover:bg-blue-600"
                              :on-click resolve-conflict-with-local}
           "Keep Mine")

        ($ :div {:class "flex items-center justify-between p-2 bg-white rounded border"}
           ($ :div
              ($ :p {:class "text-sm font-medium"} "Server Version:")
              ($ :p {:class "text-xs text-gray-600"} (str conflict-value)))
           ($ headless/Button {:class "px-3 py-1 bg-green-500 text-white rounded text-xs hover:bg-green-600"
                               :on-click resolve-conflict-with-server}
              "Use Server")))))

(defui multi-text [{:keys [value update-value]}]
  (prn value)
  (prn update-value)
  (let [[widget-state set-widget-state!] (uix/use-state (or value [""]))]
    (uix/use-effect
     (fn []
       (update-value widget-state))
     [update-value widget-state])
    ($ :div {:class "mt-1 flex flex-col flex-grow"} ;; This div remains for structure
       (for [[idx item] (map-indexed vector widget-state)]
         ($ :span {:key (str "ability-" idx) :class "flex items-center mb-2"} ;; Span remains for layout
            ($ headless/Textarea {:value item
                                  :on-change #(set-widget-state! (assoc widget-state idx (.. % -target -value)))
                                  :class "flex-grow mr-2 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"})
            ($ headless/Button {:type "button"
                                :on-click #(set-widget-state! (into (subvec widget-state 0 idx)
                                                                    (subvec widget-state (+ idx 1))))
                                :class "px-3 py-2 border border-red-500 text-red-500 rounded-md hover:bg-red-50 text-sm font-medium"}
               "-")))
       ($ headless/Button {:type "button"
                           :on-click #(set-widget-state! (conj widget-state ""))
                           :class "mt-2 px-3 py-2 border border-green-500 text-green-500 rounded-md hover:bg-green-50 text-sm font-medium self-start"}
          "+"))))

(defui card-input
  [{:keys [input-type value update-value final-classes placeholder display-label options]}]
  (case input-type
    "textarea" ($ headless/Textarea {:value (or value "")
                                     :on-change #(update-value (.. % -target -value))
                                     :class final-classes
                                     :placeholder (or placeholder (str "Enter " display-label))
                                     :rows 3})
    "select" ($ headless/Select {:on-change #(update-value (.. % -target -value))
                                 :value value
                                 :class "flex-grow mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"}
                ($ :option {:value ""} (or placeholder
                                           (str "Select " display-label)))
                (for [[option-value option-label] options]
                  ($ :option {:key option-value :value option-value} option-label)))
    "multitext" ($ multi-text {:value value :update-value update-value})
    ($ headless/Input {:type input-type
                       :value (or value "")
                       :on-change #(let [new-value (.. % -target -value)]
                                     (update-value (if (= input-type "number")
                                                     (js/parseInt new-value)
                                                     new-value)))
                       :class final-classes
                       :placeholder (or placeholder (str "Enter " display-label))})))

(defui card-field
  "Self-managing card field component with automatic validation and styling"
  [{:keys [field-key card-state label type class-name placeholder]}]
  (let [field-state (card.state/use-card-field card-state field-key)
        {:keys [value update-value is-dirty? is-loading? is-validating?
                has-error? error has-conflict? conflict-value
                resolve-conflict-with-local resolve-conflict-with-server]} field-state

        ;; Determine input type
        input-type (or type
                       (case field-key
                         (:sht :pss :def :speed :fate) "number"
                         :abilities "textarea"
                         "text"))

        ;; Determine label
        display-label (or label (-> field-key name str/capitalize))

        ;; Build CSS classes
        base-classes "w-full px-3 py-2 border rounded-md transition-colors"
        status-classes (cond
                         has-conflict? "border-orange-500 bg-orange-50"
                         has-error? "border-red-500 bg-red-50"
                         is-validating? "border-yellow-500 bg-yellow-50"
                         is-dirty? "border-blue-500 bg-blue-50"
                         :else "border-gray-300")
        final-classes (str base-classes " " status-classes " " (or class-name ""))]

    ($ headless/Field {:class "mb-4"}
       ;; Label
       ($ card-label {:display-label display-label
                      :is-loading? is-loading?
                      :is-validating? is-validating?})

       ;; Input field
       ($ card-input {:input-type input-type
                      :value value
                      :update-value update-value
                      :placeholder placeholder
                      :display-label display-label
                      :final-classes final-classes})

       ;; Status indicators
       ($ card-status {:is-loading? is-loading?
                       :is-validating? is-validating?
                       :is-dirty? is-dirty?})
       ;; Error display
       (when error
         ($ :p {:class "mt-1 text-sm text-red-600"} error))

       ;; Conflict resolution UI
       (when has-conflict?
         ($ card-conflict {:resolve-conflict-with-local resolve-conflict-with-local
                           :resolve-conflict-with-server resolve-conflict-with-server
                           :value value
                           :conflict-value conflict-value})))))

 ;; Example card components for each type

(defui player-card-editor
  [card-state]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :name :card-state card-state :label "Card Name"})
     ($ card-field {:field-key :sht :card-state card-state :label "Shot"})
     ($ card-field {:field-key :pss :card-state card-state :label "Pass"})
     ($ card-field {:field-key :def :card-state card-state :label "Defense"})
     ($ card-field {:field-key :speed :card-state card-state :label "Speed"})
     ($ card-field {:field-key :size :card-state card-state :label "Size" :type "select"})
     ($ card-field {:field-key :fate :card-state card-state :label "Fate"})
     ($ card-field {:field-key :offense :card-state card-state :label "Offense" :type "textarea"})
     ($ card-field {:field-key :defense :card-state card-state :label "Defense Text" :type "textarea"})
     ($ card-field {:field-key :abilities :card-state card-state :label "Abilities" :type "multitext"})))

(defui ability-card-editor
  [card-state]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :name :card-state card-state :label "Card Name"})
     ($ card-field {:field-key :fate :card-state card-state :label "Fate"})
     ($ card-field {:field-key :abilities :card-state card-state :label "Abilities" :type "multitext"})))

(defui play-card-editor
  [card-state]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :name :card-state card-state :label "Card Name"})
     ($ card-field {:field-key :fate :card-state card-state :label "Fate"})
     ($ card-field {:field-key :abilities :card-state card-state :label "Abilities" :type "multitext"})))

(defui split-play-card-editor
  [card-state]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :name :card-state card-state :label "Card Name"})
     ($ card-field {:field-key :fate :card-state card-state :label "Fate"})
     ($ card-field {:field-key :offense :card-state card-state :label "Offense" :type "textarea"})
     ($ card-field {:field-key :abilities :card-state card-state :label "Abilities" :type "multitext"})))

(defui coaching-card-editor
  [card-state]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :name :card-state card-state :label "Card Name"})
     ($ card-field {:field-key :fate :card-state card-state :label "Fate"})
     ($ card-field {:field-key :coaching :card-state card-state :label "Coaching" :type "textarea"})))

(defui standard-action-card-editor
  [card-state]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :name :card-state card-state :label "Card Name"})
     ($ card-field {:field-key :fate :card-state card-state :label "Fate"})
     ($ card-field {:field-key :abilities :card-state card-state :label "Abilities" :type "multitext"})))

(defui team-asset-card-editor
  [card-state]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :name :card-state card-state :label "Card Name"})
     ($ card-field {:field-key :fate :card-state card-state :label "Fate"})
     ($ card-field {:field-key :asset-power :card-state card-state :label "Asset Power" :type "textarea"})))

;; Component registry mapping card types to their editors
(def card-type-components
  {:card-type-enum/PLAYER_CARD player-card-editor
   :card-type-enum/ABILITY_CARD ability-card-editor
   :card-type-enum/PLAY_CARD play-card-editor
   :card-type-enum/SPLIT_PLAY_CARD split-play-card-editor
   :card-type-enum/COACHING_CARD coaching-card-editor
   :card-type-enum/STANDARD_ACTION_CARD standard-action-card-editor
   :card-type-enum/TEAM_ASSET_CARD team-asset-card-editor})
