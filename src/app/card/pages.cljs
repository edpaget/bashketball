(ns app.card.pages
  (:require
   ["@headlessui/react" :as headless]
   [app.authn.provider :as authn]
   [app.card.edit :refer [edit-card]]
   [app.card.graphql-types :as card.gql-types]
   [app.card.hooks :as card.hooks]
   [app.card.show :refer [show-card]]
   [app.card.state :as card-state]
   [app.graphql.client :as gql.client]
   [app.models :as models]
   [app.router :as router]
   [uix.core :as uix :refer [defui $]]
   [app.card.state :as card.state]))

(defui cards-index []
  (let [{:keys [loading data]} (gql.client/use-query {:Query/cards [::models/Card]} "getAllCards")]
    ($ :div {:className "container mx-auto p-4"} ; Added container and padding
       (cond
         loading ($ :p {:className "text-gray-500"} "loading cards...") ; Styled loading text
         data ($ :ul {:className "space-y-2 mb-4"} ; Added vertical spacing and margin
                 (for [card (:cards data)]
                   ($ :li {:key (:name card) :className "bg-white p-2 rounded shadow"} ; Styled list items
                      ($ :a {:href (router/href :cards-show {:id (:name card)})
                             :className "text-blue-600 hover:underline"} ; Styled link
                         (:name card))))))
       ($ :a {:href (router/href :cards-new)
              :className "inline-block bg-blue-500 hover:bg-blue-700 text-white font-bold py-2 px-4 rounded"} ; Styled "New Card" link as a button
          "New Card"))))

(defui cards-show []
  (let [card-id (-> (router/use-router) :path-params :id)]

    ;; Wrap content in a styled container
    ($ card.state/with-card {:name card-id}
       ($ :div {:className "container mx-auto p-4 grid grid-cols-1 md:grid-cols-2 gap-4"}
          ($ :div {}                    ; Column for showing the card
             ($ show-card))
          ($ :div {}             ; Column for edit form (conditionally rendered)
             ($ authn/login-required {:show-prompt false}
                ($ edit-card {:new? false})))))))

(defui cards-new []
  (comment
    (let [;; Initialize with basic card structure
          initial-card {:card-type :card-type-enum/INVALID
                        :name ""
                        :fate 1}

          ;; Use unified state management
          card-state (card-state/use-card-state initial-card
                                                :auto-save? false
                                                :validate-on-change? true)

          ;; Get unified mutations
          {:keys [is-creating? create state]} (card.hooks/use-card-mutations (:card-type (:card card-state)))

          ;; Handle form submission
          handle-submit (fn []
                          (when-not (:has-errors? card-state)
                            (create {:variables (:card card-state)})))]

      ;; Navigate to card after creation
      (uix/use-effect
       (fn []
         (when-let [new-card (-> state :data vals first)]
           (router/navigate! :cards-show {:id (:name new-card)})))
       [state])

      ;; Wrap content in a styled container, similar to cards-show
      ($ :div {:className "container mx-auto p-4 grid grid-cols-1 md:grid-cols-2 gap-4"}
         ($ :div {} ; Column for showing the card preview
            ($ show-card (:card card-state)))
         ($ :div {} ; Column for edit form
            ($ authn/login-required {:show-prompt false}
               ($ edit-card {:card (:card card-state)
                             :new? true
                             :update-card-field (:update-field card-state)})

               ;; Error display
               (when (:has-errors? card-state)
                 ($ :div {:class "mt-4 p-3 bg-red-50 border border-red-200 rounded-md max-w-2xl mx-auto"}
                    ($ :h4 {:class "text-red-800 font-medium mb-2"} "Please fix the following errors:")
                    ($ :ul {:class "text-red-700 text-sm list-disc list-inside"}
                       (for [[field error] (:errors card-state)]
                         ($ :li {:key field} (str (name field) ": " error))))))

               ;; Submit button
               ($ :div {:class "mt-6 flex justify-end max-w-2xl mx-auto"}
                  ($ headless/Button {:type "button"
                                      :on-click handle-submit
                                      :disabled (or is-creating?
                                                    (:has-errors? card-state)
                                                    (empty? (get-in card-state [:card :name])))
                                      :class "bg-indigo-600 hover:bg-indigo-700 text-white font-bold py-2 px-4 rounded-md focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:bg-gray-400"}
                     (if is-creating? "Creating..." "Create Card")))))))))
