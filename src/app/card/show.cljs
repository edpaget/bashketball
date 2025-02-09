(ns app.card.show
  (:require [app.card.types :as card-types]
            [uix.core :as uix :refer [defui $]]
            [uix.dom]))

(defui split-abilities [{:keys [offense defense]}]
  ($ :div.abilities.split
     ($ :div.ability
        ($ :p offense))
     ($ :div.ability
        ($ :p defense))))

(defui list-abilities [{:keys [abilities]}]
  ($ :div.abilities
     (for [[idx ability] (map-indexed vector abilities)]
       ($ :div.ability {:key (str "ability-idx-" idx)}
          ($ :p ability)))))

(defui show-card [{:keys [type] :as card}]
  ($ :div.card
     ($ :div.card-header
        ($ :h1 (:name card))
        (condp = type
          :ability nil
          :player ($ :small (str (:deck-size card) " cards"))
          ($ :small (:fate card))))
     ($ :div.card-art
        (if-let [img (:img card)]
          ($ :img {:src img})
          ($ :div.card-img-placeholder))
        ($ :h2.card-type (get-in card-types/types [type :type-label])))
     (condp = type
       :player ($ :<>
                  ($ :div.player-stats-top
                     ($ :ul
                        ($ :li (str "SHT - " (:sht card)))
                        ($ :li (str "PSS - " (:pss card)))
                        ($ :li (str "DEF - " (:def card)))))
                  ($ :div.player-stats-bottom
                     ($ :ul
                        ($ :li (str "SPEED - " (:speed card)))
                        ($ :li (str "SIZE - " (:size card)))))
                  ($ list-abilities card))
       :coaching ($ :div.ability
                    ($ :p (:coaching card)))
       :play ($ :div.ability
                    ($ :p (:play card)))
       :team-asset ($ :div.ability
                    ($ :p (:asset-power card)))
       :split-play ($ split-abilities card)
       :standard-action ($ split-abilities card)
       :ability ($ list-abilities card)
       ($ :p "unimplemented"))))
