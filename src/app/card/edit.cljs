(ns app.card.edit
  (:require
   ["@headlessui/react" :as headless]
   [app.asset.uploader :as a.uploader]
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
  [{:keys [card new?]}]
  (let [card-state (card.state/use-card-state card
                                              :auto-save? true
                                              :debounce-ms 500
                                              :validate-on-change? true)

        ;; Extract card and state functions
        current-card (:card card-state)
        update-field (:update-field card-state)

        ;; Track selected card type for new cards
        [selected-type set-selected-type] (uix/use-state (:card-type current-card))

        ;; Get the appropriate editor component for the selected type
        editor-component (get card.components/card-type-components selected-type)
        reset-fn (:reset-card card-state)]

    (uix/use-effect
     (fn []
       (when card
         (reset-fn card)))
     [reset-fn card])

    ($ :div {:class "p-6 bg-white shadow-lg rounded-lg max-w-2xl mx-auto my-8"}
       ($ :div {:class "space-y-6"}
          ($ :h1 {:class "text-3xl font-bold text-gray-900 mb-6 text-center"}
             (if new? "Create Card" "Edit Card"))

          ;; Card type selection (for new cards) or display (for existing cards)
          (if new?
            ($ card-type-selector {:selected-type selected-type
                                   :on-type-change (fn [new-type]
                                                     (set-selected-type new-type)
                                                     (update-field :card-type new-type))})
            ($ headless/Field {:class "mb-6"}
               ($ headless/Label {:class "block text-sm font-medium text-gray-700 mb-2"} "Card Type")
               ($ :div {:class "w-full px-3 py-2 text-gray-500 bg-gray-100 rounded-md"}
                  (get card-types/->type-label (:card-type current-card) "Unknown Type"))))

          ;; Asset uploader
          ($ a.uploader/asset-upload {:update-card-field update-field})

          ;; Render the appropriate card editor component based on type
          (when editor-component
            ($ editor-component card-state))

          ;; Show message if no editor available for the selected type
          (when (and selected-type (not editor-component))
            ($ :div {:class "p-4 bg-yellow-50 border border-yellow-200 rounded-md"}
               ($ :p {:class "text-sm text-yellow-800"}
                  "No editor available for card type: " (name selected-type))))))))

;; Remove unused components
