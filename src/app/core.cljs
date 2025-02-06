(ns app.core
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [app.card.edit :refer [edit-card]]
            [app.card.reducer :as card-reducer ]
            [app.card.show :refer [show-card player-card]]))

(defui app []
  (let [[card dispatch-card!] (uix/use-reducer card-reducer/card-state-reducer player-card)]
    ($ :div.app-container
       ($ :div.navbar)
       ($ :div.content
          ($ edit-card {:card card :update-card-field (card-reducer/update-field-dispatch dispatch-card!)})
          ($ show-card card))
       ($ :div.footer))))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn render []
  (uix.dom/render-root ($ app) root))

(defn ^:export init []
  (render))
