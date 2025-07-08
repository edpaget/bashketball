(ns app.card.edit
  (:require
   [app.card.components :as card.components]
   [app.card.state :as card.state]
   [app.card.types :as card.types]
   [uix.core :as uix :refer [defui $]]))

(defui edit-card
  [{:keys [new?]}]
  (let [current-card (card.state/use-current-card)]

    ($ :div {:class "p-6 bg-white shadow-lg rounded-lg max-w-2xl mx-auto my-8"}
       ($ :div {:class "space-y-6"}
          ($ :h1 {:class "text-3xl font-bold text-gray-900 mb-6 text-center"}
             (if new? "Create Card" "Edit Card"))

          ($ card.components/card-field {:field-key :card-type
                                         :label "Card Type"
                                         :disabled (not new?)
                                         :type "select"
                                         :options card.types/->type-label})
          ($ card.components/card-field {:field-key :name
                                         :label "Name"
                                         :disabled (not new?)})
          ($ card.components/card-upload-field {:pass-blob? new?})

          ;; Render the appropriate card editor component based on type
          (when-let [editor-component (get card.components/card-type-components (:card-type current-card))]
            ($ editor-component))))))
