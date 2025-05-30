(ns app.card.pages
  (:require
   [uix.core :as uix :refer [defui $]]
   [app.router :as router]
   [app.graphql.client :as graphql.client]
   [app.authn.provider :as authn]
   [app.card.edit :refer [edit-card]]
   [app.card.reducer :as card-reducer]
   [app.card.show :refer [show-card]]
   [app.models :as models]))

(defui cards-index []
  (let [{:keys [loading data]} (graphql.client/use-query {:Query/cards [::models/Card]} "getAllCards")]
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
  (let [card-id (-> (router/use-router) :path-params :id)
        {:keys [loading data]} (graphql.client/use-query
                                {:Query/card '([::models/GameCard] :name)}
                                "getMostRecentCardVersionByName"
                                [[:name :string]]
                                {:name card-id})
        [card dispatch-card!] (uix/use-reducer card-reducer/card-state-reducer (:card data))]
    (prn data)
    ;; Wrap content in a styled container
    ($ :div {:className "container mx-auto p-4 grid grid-cols-1 md:grid-cols-2 gap-4"}
       ($ :div {} ; Column for edit form (conditionally rendered)
          ($ authn/login-required {:show-prompt false}
             (when-not loading
               ($ edit-card {:card card
                             :update-card-field (card-reducer/update-field-dispatch dispatch-card!)}))))
       ($ :div {} ; Column for showing the card
          ($ show-card card)))))

(defui cards-new []
  (let [[card dispatch-card!] (uix/use-reducer card-reducer/card-state-reducer {})]
    ;; Wrap content in a styled container, similar to cards-show
    ($ :div {:className "container mx-auto p-4 grid grid-cols-1 md:grid-cols-2 gap-4"}
       ($ :div {} ; Column for edit form
          ($ authn/login-required {:show-prompt false}
             ($ edit-card {:card card
                           :update-card-field (card-reducer/update-field-dispatch dispatch-card!)})))
       ($ :div {} ; Column for showing the card preview
          ($ show-card card)))))
