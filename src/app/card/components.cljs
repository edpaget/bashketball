(ns app.card.components
  (:require
   ["@headlessui/react" :as headless]
   [app.asset.uploader :as a.uploader]
   [app.card.state :as card.state]
   [clojure.string :as str]
   [uix.core :as uix :refer [defui $]]))

(defui card-label
  [{:keys [display-label loading?]}]
  ($ headless/Label {:class "block text-sm font-medium text-gray-700 mb-1"}
     display-label
     (when loading? ($ :span {:class "text-purple-600 ml-2"} "💾"))))

(defui card-status
  [{:keys [dirty? loading?]}]
  ($ :div {:class "mt-1 flex items-center text-xs space-x-2"}
     (when dirty?
       ($ :span {:class "text-blue-600"} "📝 Modified"))
     (when loading?
       ($ :span {:class "text-purple-600"} "💾 Saving..."))))

(defui multi-text [{:keys [value update-value]}]
  ($ :div {:class "mt-1 flex flex-col flex-grow"} ;; This div remains for structure
     (for [[idx item] (map-indexed vector value)]
       ($ :span {:key (str "ability-" idx) :class "flex items-center mb-2"} ;; Span remains for layout
          ($ headless/Textarea {:value item
                                :on-change #(update-value (assoc value idx (.. % -target -value)))
                                :class "flex-grow mr-2 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"})
          ($ headless/Button {:type "button"
                              :on-click #(update-value (into (subvec value 0 idx)
                                                             (subvec value (+ idx 1))))
                              :class "px-3 py-2 border border-red-500 text-red-500 rounded-md hover:bg-red-50 text-sm font-medium"}
             "-")))
     ($ headless/Button {:type "button"
                         :on-click #(update-value (conj value ""))
                         :class "mt-2 px-3 py-2 border border-green-500 text-green-500 rounded-md hover:bg-green-50 text-sm font-medium self-start"}
        "+")))

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
  [{:keys [field-key label type class-name placeholder]}]
  (let [field-state (card.state/use-card-field field-key)
        {:keys [value update-value dirty? loading?
                has-error? error]} field-state

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
                         has-error? "border-red-500 bg-red-50"
                         dirty? "border-blue-500 bg-blue-50"
                         :else "border-gray-300")
        final-classes (str base-classes " " status-classes " " (or class-name ""))]

    ($ headless/Field {:class "mb-4"}
       ;; Label
       ($ card-label {:display-label display-label
                      :loading? loading?})

       ;; Input field
       ($ card-input {:input-type input-type
                      :value value
                      :update-value update-value
                      :placeholder placeholder
                      :display-label display-label
                      :final-classes final-classes})

       ;; Status indicators
       ($ card-status {:loading? loading?
                       :dirty? dirty?})
       ;; Error display
       (when error
         ($ :p {:class "mt-1 text-sm text-red-600"} error)))))

;; Example card components for each type

(defui player-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ a.uploader/asset-upload {:update-card-field update-field})
     ($ card-field {:field-key :sht :label "Shot"})
     ($ card-field {:field-key :pss :label "Pass"})
     ($ card-field {:field-key :def :label "Defense"})
     ($ card-field {:field-key :speed :label "Speed"})
     ($ card-field {:field-key :size :label "Size" :type "select"})
     ($ card-field {:field-key :abilities :label "Abilities" :type "multitext"})))

(defui ability-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ a.uploader/asset-upload {:update-card-field update-field})
     ($ card-field {:field-key :fate :label "Fate"})
     ($ card-field {:field-key :abilities :label "Abilities" :type "multitext"})))

(defui play-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ a.uploader/asset-upload {:update-card-field update-field})
     ($ card-field {:field-key :fate :label "Fate"})
     ($ card-field {:field-key :abilities :label "Abilities" :type "multitext"})))

(defui split-play-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ a.uploader/asset-upload {:update-card-field update-field})
     ($ card-field {:field-key :fate :label "Fate"})
     ($ card-field {:field-key :offense :label "Offense" :type "textarea"})
     ($ card-field {:field-key :abilities :label "Abilities" :type "multitext"})))

(defui coaching-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ a.uploader/asset-upload {:update-card-field update-field})
     ($ card-field {:field-key :fate :label "Fate"})
     ($ card-field {:field-key :coaching :label "Coaching" :type "textarea"})))

(defui standard-action-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ a.uploader/asset-upload {:update-card-field update-field})
     ($ card-field {:field-key :fate :label "Fate"})
     ($ card-field {:field-key :abilities :label "Abilities" :type "multitext"})))

(defui team-asset-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ a.uploader/asset-upload {:update-card-field update-field})
     ($ card-field {:field-key :fate :label "Fate"})
     ($ card-field {:field-key :asset-power :label "Asset Power" :type "textarea"})))

;; Component registry mapping card types to their editors
(def card-type-components
  {:card-type-enum/PLAYER_CARD player-card-editor
   :card-type-enum/ABILITY_CARD ability-card-editor
   :card-type-enum/PLAY_CARD play-card-editor
   :card-type-enum/SPLIT_PLAY_CARD split-play-card-editor
   :card-type-enum/COACHING_CARD coaching-card-editor
   :card-type-enum/STANDARD_ACTION_CARD standard-action-card-editor
   :card-type-enum/TEAM_ASSET_CARD team-asset-card-editor})
