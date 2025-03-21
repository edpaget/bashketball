(ns app.card.pages
  (:require [uix.core :as uix :refer [defui $]]
            [app.router :as router]
            [app.authn.provider :as authn]
            [app.card.edit :refer [edit-card]]
            [app.card.reducer :as card-reducer]
            [app.card.show :refer [show-card]]))

(defui cards-index []
  ($ :a {:href (router/href :cards-new)}
     "New Card"))

(defui cards-show []
  (let [card-id (-> (router/use-router) :path-params :id)
        _ (prn card-id)
        [card dispatch-card!] (uix/use-reducer card-reducer/card-state-reducer {})]
    ($ :<>
       ($ authn/login-required {:show-prompt false}
          ($ edit-card {:card card
                        :update-card-field (card-reducer/update-field-dispatch dispatch-card!)}))
       ($ show-card card))))
