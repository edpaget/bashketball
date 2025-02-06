(ns app.card.show
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]))

(def player-card {:type :player
                  :name "basic player"
                  :deck-size 5
                  :sht 1
                  :pss 1
                  :def 1
                  :speed 5
                  :size "sm"
                  :abilities []
                  :img nil})

(defui show-card [card]
  ($ :div.card
     (condp = (:type card)
       :player ($ :<>
                  ($ :div.card-header
                     ($ :h1 (:name card))
                     ($ :small (str (:deck-size card) " cards")))
                  ($ :div.card-art
                     (if-let [img (:img card)]
                       ($ :img {:src img})
                       ($ :div.card-img-placeholder))
                     ($ :h2.card-type "Player"))
                  ($ :div.player-stats-top
                     ($ :ul
                        ($ :li (str "SHT - " (:sht card)))
                        ($ :li (str "PSS - " (:pss card)))
                        ($ :li (str "DEF - " (:def card)))))
                  ($ :div.player-stats-bottom
                     ($ :ul
                        ($ :li (str "SPEED - " (:speed card)))
                        ($ :li (str "SIZE - " (:size card)))))
                  ($ :div.abilities
                     (for [ability (:abilities card)]
                       ($ :div.ability {:key ability}
                          ($ :p ability)))))
       ($ :div.no-type
          ($ :h1 "Select Card Type")))))
