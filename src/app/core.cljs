(ns app.core
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [app.router :as router]
            [app.card.edit :refer [edit-card]]
            [app.card.reducer :as card-reducer ]
            [app.card.show :refer [show-card player-card]]))

(defui home-page []
  ($ :a {:href (router/href :cards-index)}
     "Cards"))

(defui cards-index []
  ($ :a {:href (router/href :cards-new)}
     "New Card"))

(defui cards-show []
  (let [[card dispatch-card!] (uix/use-reducer card-reducer/card-state-reducer player-card)]
    ($ :<>
       ($ edit-card {:card card :update-card-field (card-reducer/update-field-dispatch dispatch-card!)})
       ($ show-card card))))

  (defui app []
    ($ router/router {:router-store router/router-store}
       ($ :div.app-container
          ($ :div.navbar)
          ($ :div.content
             ($ router/route {:route-name :home-page}
                ($ home-page))
             ($ router/route {:route-name :cards-index}
                ($ cards-index))
             ($ router/route {:route-name :cards-new}
                ($ cards-show))
             ($ router/route {:route-name :cards-show}
                ($ cards-show)))
          ($ :div.footer))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn render []
  (uix.dom/render-root ($ app) root))

(defn ^:export init []
  (render))
