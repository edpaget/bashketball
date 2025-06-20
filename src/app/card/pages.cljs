(ns app.card.pages
  (:require
   [uix.core :as uix :refer [defui $]]
   [app.router :as router]
   [app.graphql.client :as gql.client]
   [app.authn.provider :as authn]
   [app.card.edit :refer [edit-card]]
   [app.card.hooks :as card.hooks]
   [app.card.reducer :as card-reducer]
   [app.card.show :refer [show-card]]
   [app.models :as models]))

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

(defui card-autoupdate [{:keys [card dispatch-card!]}]
  (let [[update-card! {:keys [data loading]}] (card.hooks/use-card-update (:card-type card))
        debounced-card (card.hooks/use-debounce card 500)
        is-initial-mount (uix/use-ref true)
        last-synced-card (uix/use-ref card)]

    (uix/use-effect
     (fn []
       ;; Prepare card data for comparison by removing server-set fields
       (let [card-to-compare (dissoc debounced-card
                                     :updated-at
                                     :created-at
                                     :game-asset
                                     :__typename)
             last-synced (dissoc @last-synced-card
                                 :updated-at
                                 :created-at
                                 :game-asset
                                 :__typename)]
         (cond
           ;; Don't run on initial mount
           @is-initial-mount
           (reset! is-initial-mount false)

           ;; Don't run if a mutation is already in flight
           loading
           nil

           ;; Don't run if the user's debounced changes match the last synced state
           (= card-to-compare last-synced)
           nil

           ;; Otherwise, perform the update, sending only the necessary fields
           :else
           (do
             (reset! last-synced-card card-to-compare)
             (update-card! {:variables card-to-compare})))))
     ;; Dependencies for this effect
     [debounced-card loading update-card!])

    ;; Effect to update local state when the server responds with new data
    (uix/use-effect
     (fn []
       (when-let [updated-card (-> data vals first)]
         ;; Update the main state via the reducer
         (card-reducer/reset-state! dispatch-card! updated-card)))
     [dispatch-card! data]))

  ($ edit-card {:card card
                :new? false
                :update-card-field (card-reducer/update-field-dispatch dispatch-card!)}))

(defui cards-show []
  (let [card-id (-> (router/use-router) :path-params :id)
        {:keys [data]} (gql.client/use-query
                        {:Query/card (list [::models/GameCard
                                            [::models/PlayerCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                            [::models/AbilityCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                            [::models/SplitPlayCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                            [::models/PlayCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                            [::models/CoachingCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                            [::models/StandardActionCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                            [::models/TeamAssetCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]]
                                           :name)}
                        "getMostRecentCardVersionByName"
                        [[:name :string]]
                        {:name card-id})
        [card dispatch-card!] (uix/use-reducer card-reducer/card-state-reducer {})]
    (uix/use-effect (fn []
                      (when-let [new-card (:card data)]
                        (card-reducer/reset-state! dispatch-card! new-card))) [data])
  ;; Wrap content in a styled container
    ($ :div {:className "container mx-auto p-4 grid grid-cols-1 md:grid-cols-2 gap-4"}
       ($ :div {} ; Column for showing the card
          ($ show-card card))
       ($ :div {} ; Column for edit form (conditionally rendered)
          ($ authn/login-required {:show-prompt false}
             (when (not-empty card)
               ($ card-autoupdate {:card card :dispatch-card! dispatch-card!})))))))

(defui cards-new []
  (let [[card dispatch-card!] (uix/use-reducer card-reducer/card-state-reducer {})]
    ;; Wrap content in a styled container, similar to cards-show
    ($ :div {:className "container mx-auto p-4 grid grid-cols-1 md:grid-cols-2 gap-4"}
       ($ :div {} ; Column for showing the card preview
          ($ show-card card))
       ($ :div {} ; Column for edit form
          ($ authn/login-required {:show-prompt false}
             ($ edit-card {:card card
                           :new? true
                           :update-card-field (card-reducer/update-field-dispatch dispatch-card!)}))))))
