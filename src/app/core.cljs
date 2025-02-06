(ns app.core
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [app.card.show :refer [show-card player-card]]))

(defui app []
  ($ :div.app-container
     ($ :div.navbar)
     ($ :div.content
        ($ show-card player-card))
     ($ :div.footer)))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn render []
  (uix.dom/render-root ($ app) root))

(defn ^:export init []
  (render))
