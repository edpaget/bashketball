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
        editor-component (get card.components/card-type-components (:card-type current-card))]

    (prn current-card)
          (prn editor-component)
    ($ :div {:class "p-6 bg-white shadow-lg rounded-lg max-w-2xl mx-auto my-8"}
       ($ :div {:class "space-y-6"}
          ($ :h1 {:class "text-3xl font-bold text-gray-900 mb-6 text-center"}
             (if new? "Create Card" "Edit Card"))

          ;; Card type selection (for new cards) or display (for existing cards)
          (if new?
            (comment
              ($ card-type-selector {:selected-type selected-type
                                     :on-type-change (fn [new-type]
                                                       (set-selected-type new-type)
                                                       (update-field :card-type new-type))}))
            ($ headless/Field {:class "mb-6"}
               ($ headless/Label {:class "block text-sm font-medium text-gray-700 mb-2"} "Card Type")
               ($ :div {:class "w-full px-3 py-2 text-gray-500 bg-gray-100 rounded-md"}
                  (get card-types/->type-label (:card-type current-card) "Unknown Type"))))

          ;; Render the appropriate card editor component based on type
          (when editor-component
            ($ editor-component))))))

;; Remove unused components
