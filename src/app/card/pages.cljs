(ns app.card.pages
  (:require [uix.core :as uix :refer [defui $]]
            [app.router :as router]
            [app.graphql.client :as graphql.client]
            [app.authn.provider :as authn]
            [app.card.edit :refer [edit-card]]
            [app.card.reducer :as card-reducer]
            [app.card.show :refer [show-card]]))

(defui cards-index []
  (let [{:keys [loading data]} (graphql.client/use-query "query { cards { ... on PlayerCard { name cardType } } }")]
    (prn data)
    ($ :div {:className "card-index"}
       (cond
         loading ($ :p "loading cards...")
         data ($ :ul {:className "card-list"}
                 (for [card (:cards data)]
                   ($ :li {:key (:name card)}
                      ($ :a {:href (router/href :cards-show {:id (:name card)})}
                         (:name card))))))
       ($ :a {:href (router/href :cards-new)}
          "New Card"))))

(def ^:private get-card
   "query GetCard($cardName: String!) { card(cardName: $cardName) { ... on PlayerCard { name cardType } } }")

(defui cards-show []
  (let [card-id (-> (router/use-router) :path-params :id)
        {:keys [loading data]} (graphql.client/use-query get-card {:card-name card-id})
        _ (prn (:card data))
        [card dispatch-card!] (uix/use-reducer card-reducer/card-state-reducer (:card data))]
    ($ :<>
       ($ authn/login-required {:show-prompt false}
          (when-not loading
            ($ edit-card {:card card
                          :update-card-field (card-reducer/update-field-dispatch dispatch-card!)})))
       ($ show-card card))))

(defui cards-new []
  (let [[card dispatch-card!] (uix/use-reducer card-reducer/card-state-reducer {})]
    ($ :<>
       ($ authn/login-required {:show-prompt false}
          ($ edit-card {:card card
                        :update-card-field (card-reducer/update-field-dispatch dispatch-card!)}))
       ($ show-card card))))
