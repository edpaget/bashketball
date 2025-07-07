(ns app.card.edit
  (:require
   ["@headlessui/react" :as headless]
   [app.card.components :as card.components]
   [app.card.state :as card.state]
   [app.card.types :as card-types]
   [uix.core :as uix :refer [defui $]]))

(defui card-type-selector
  [{:keys [selected-type on-type-change]}]
  ($ headless/Field {:class "mb-6"}
     ($ headless/Label {:class "block text-sm font-medium text-gray-700 mb-2"} "Card Type")
     ($ headless/Select {:value (name (or selected-type ""))
                         :on-change #(on-type-change (keyword :card-type-enum (.. % -target -value)))
                         :class "w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500"}
        ($ :option {:value ""} "Select Card Type")
        (for [[type type-label] card-types/->type-label]
          ($ :option {:key (name type) :value (name type)} type-label)))))

(defui edit-card
  [{:keys [new?]}]
  (let [current-card (card.state/use-current-card)

        ;; Get the appropriate editor component for the selected type
        ]
    ($ :div {:class "p-6 bg-white shadow-lg rounded-lg max-w-2xl mx-auto my-8"}
       ($ :div {:class "space-y-6"}
          ($ :h1 {:class "text-3xl font-bold text-gray-900 mb-6 text-center"}
             (if new? "Create Card" "Edit Card"))

          ($ card.components/card-field {:field-key :card-type
                                         :label "Card Type"
                                         :disabled new?
                                         :type "select"})
          ($ card.components/card-field {:field-key :name
                                         :label "Name"
                                         :disabled new?})

          ;; Render the appropriate card editor component based on type
          (when-let [editor-component (get card.components/card-type-components (:card-type current-card))]
            ($ editor-component))))))

;; Remove unused components
