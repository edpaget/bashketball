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
  [{:keys [dirty?]}]
  ($ :div {:class "mt-1 flex items-center text-xs space-x-2"}
     (when dirty?
       ($ :span {:class "text-blue-600"} "📝 Modified"))))

(defui multi-text [{:keys [value update-value disabled]}]
  ($ :div {:class "mt-1 flex flex-col flex-grow"} ;; This div remains for structure
     (for [[idx item] (map-indexed vector value)]
       ($ :span {:key (str "ability-" idx) :class "flex items-center mb-2"} ;; Span remains for layout
          ($ headless/Textarea {:value item
                                :disabled disabled
                                :on-change #(update-value (assoc (vec value) idx (.. % -target -value)))
                                :class "flex-grow mr-2 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"})
          ($ headless/Button {:type "button"
                              :disabled disabled
                              :on-click #(update-value (into (subvec value 0 idx)
                                                             (subvec value (+ idx 1))))
                              :class "px-3 py-2 border border-red-500 text-red-500 rounded-md hover:bg-red-50 text-sm font-medium"}
             "-")))
     ($ headless/Button {:type "button"
                         :disabled disabled
                         :on-click #(update-value (conj value ""))
                         :class "mt-2 px-3 py-2 border border-green-500 text-green-500 rounded-md hover:bg-green-50 text-sm font-medium self-start"}
        "+")))

(defui card-input
  [{:keys [field-key input-type value update-value final-classes placeholder display-label options disabled]}]
  (case input-type
    "textarea" ($ headless/Textarea {:value (or value "")
                                     :name (name field-key)
                                     :disabled disabled
                                     :on-change #(update-value (.. % -target -value))
                                     :class final-classes
                                     :placeholder (or placeholder (str "Enter " display-label))
                                     :rows 3})
    "select" (let [value->kw (zipmap (->> options keys (map name))
                                     (keys options))]
               ($ headless/Select {:on-change #(update-value
                                                (value->kw
                                                 (.. % -target -value)))
                                   :name (name field-key)
                                   :disabled disabled
                                   :value value
                                   :class "flex-grow mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"}
                  ($ :option {:value ""} (or placeholder
                                             (str "Select " display-label)))
                  (for [[option-value option-label] options]
                    ($ :option {:key (name option-value) :value option-value} option-label))))
    "multitext" ($ multi-text {:value value :update-value update-value :field-key field-key})
    ($ headless/Input {:type input-type
                       :name (name field-key)
                       :disabled disabled
                       :value (or value "")
                       :on-change #(let [new-value (.. % -target -value)]
                                     (update-value (if (= input-type "number")
                                                     (js/parseInt new-value)
                                                     new-value)))
                       :class final-classes
                       :placeholder (or placeholder (str "Enter " display-label))})))

(defui card-field
  "Self-managing card field component with automatic validation and styling"
  [{:keys [field-key label type class-name placeholder disabled options]}]
  (let [field-state (card.state/use-card-field field-key)
        {:keys [value update-value dirty? loading?
                errored? error]} field-state

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
                         errored? "border-red-500 bg-red-50"
                         dirty? "border-blue-500 bg-blue-50"
                         :else "border-gray-300")
        final-classes (str base-classes " " status-classes " " (or class-name ""))]

    ($ headless/Field {:class "mb-4"}
       ;; Label
       ($ card-label {:display-label display-label
                      :loading? loading?})

       ;; Input field
       ($ card-input {:input-type input-type
                      :field-key field-key
                      :value value
                      :disabled disabled
                      :update-value update-value
                      :placeholder placeholder
                      :display-label display-label
                      :final-classes final-classes
                      :options options})

       ;; Status indicators
       ($ card-status {:loading? loading?
                       :dirty? dirty?})
       ;; Error display
       (when error
         ($ :p {:class "mt-1 text-sm text-red-600"} error)))))

(defui card-upload-field
  [{:keys [pass-blob?]}]
  (let [{:keys [update-value]} (card.state/use-card-field :game-asset-id)
        {update-img-url :update-value} (card.state/use-card-field :game-asset)]
    ($ a.uploader/asset-upload (cond-> {:update-card-field update-value}
                                 pass-blob? (assoc :update-img-url update-img-url)))))

;; Example card components for each type

(defui player-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :sht :label "Shot"})
     ($ card-field {:field-key :pss :label "Pass"})
     ($ card-field {:field-key :def :label "Defense"})
     ($ card-field {:field-key :speed :label "Speed"})
     ($ card-field {:field-key :size :label "Size" :type "select"})
     ($ card-field {:field-key :abilities :label "Abilities" :type "multitext"})))

(defui ability-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :fate :label "Fate"})
     ($ card-field {:field-key :abilities :label "Abilities" :type "multitext"})))

(defui play-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :fate :label "Fate"})
     ($ card-field {:field-key :abilities :label "Abilities" :type "multitext"})))

(defui split-play-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :fate :label "Fate"})
     ($ card-field {:field-key :offense :label "Offense" :type "textarea"})
     ($ card-field {:field-key :abilities :label "Abilities" :type "multitext"})))

(defui coaching-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :fate :label "Fate"})
     ($ card-field {:field-key :coaching :label "Coaching" :type "textarea"})))

(defui standard-action-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
     ($ card-field {:field-key :fate :label "Fate"})
     ($ card-field {:field-key :abilities :label "Abilities" :type "multitext"})))

(defui team-asset-card-editor
  [{:keys [update-field]}]
  ($ :div {:class "space-y-4"}
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
