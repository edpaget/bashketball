(ns app.core
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [app.router :as router]
            [app.authn.provider :as authn]
            [app.card.edit :refer [edit-card]]
            [app.card.reducer :as card-reducer ]
            [app.card.show :refer [show-card]]))

(defui home-page []
  ($ :a {:href (router/href :cards-index)}
     "Cards"))

(defui cards-index []
  ($ :a {:href (router/href :cards-new)}
     "New Card"))

(defui cards-show []
  (let [[card dispatch-card!] (uix/use-reducer card-reducer/card-state-reducer {})]
    ($ :<>
       ($ edit-card {:card card :update-card-field (card-reducer/update-field-dispatch dispatch-card!)})
       ($ show-card card))))

(defui app []
  ($ router/router {:router-store router/router-store}
     ($ authn/authn
        ($ :div.app-container
           ($ :div.navbar
              ($ :h1 "Blood Basket")
              ($ authn/login-required ($ authn/logout-button)))
           ($ :div.content
              ($ :<>
                 ($ router/route {:route-name :home-page}
                    ($ home-page))
                 ($ router/route {:route-name :cards-index}
                    ($ cards-index))
                 ($ router/route {:route-name :cards-new}
                    ($ authn/login-required {:show-prompt true}
                       ($ cards-show)))
                 ($ router/route {:route-name :cards-show}
                    ($ cards-show)))))
        ($ :div.footer))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn render []
  (uix.dom/render-root ($ app) root))

(defn ^:export init []
  (render))
