(ns app.card.pages
  (:require
   ["@headlessui/react" :as headless]
   [app.authn.provider :as authn]
   [app.card.edit :refer [edit-card]]
   [app.card.graphql-types :as card.gql-types]
   [app.card.show :refer [show-card]]
   [app.card.state :as card.state]
   [app.graphql.client :as gql.client]
   [app.models :as models]
   [app.router :as router]
   [uix.core :as uix :refer [defui $]]))

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
  ($ card.state/with-card {:new? true}
     ($ :div {:className "container mx-auto p-4 grid grid-cols-1 md:grid-cols-2 gap-4"}
        ($ :div {}
           ($ show-card))
        ($ :div {}
           ($ edit-card {:new? true})))))
