(ns app.card.edit
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]))

(defui text-widget [{:keys [name label field card on-change]}]
  (let [for-value (str name "-input")]
    ($ :div.widget
       ($ :label {:for for-value} label)
       ($ :input {:value (get card field "")
                  :id for-value
                  :name name
                  :on-change #(on-change field (.. % -target -value))}))))

(def player-field-desc [{:name "deck-size"
                         :label "Deck Size"
                         :field :deck-size}
                        {:name "sht"
                         :label "Shot"
                         :field :sht}
                        {:name "pss"
                         :label "Pass"
                         :field :pss}
                        {:name "def"
                         :label "Defense"
                         :field :def}
                        {:name "speed"
                         :label "Speed"
                         :field :speed}])

(defui player-fields [{:keys [card update-card-field]}]
  ($ :<>
     (for [field player-field-desc]
       ($ text-widget (merge {:key (:name field)
                              :card card
                              :on-change update-card-field}
                             field)))
     ($ :div.widget
        ($ :label {:for "size-select"} "Card Type")
        ($ :select {:name "size-type"
                    :id "size-select"
                    :on-change #(update-card-field :size (.. % -target -value))
                    :value (name (get card :size ""))}
           ($ :option {:value ""} "Select Player Size ")
           ($ :option {:value "sm"} "Small")
           ($ :option {:value "md"} "Medium")
           ($ :option {:value "lg"} "Large")))
     ))

(defui edit-card [{:keys [card update-card-field]}]
  ($ :div.card-edit
     ($ :form
        ($ :h1 "Edit Card")
        ($ :div.widget
           ($ :label {:for "card-type-select"} "Card Type")
           ($ :select {:name "card-type"
                       :id "card-type-select"
                       :on-change #(update-card-field :type (keyword (.. % -target -value)))
                       :value (name (get card :type ""))}
              ($ :option {:value ""} "Select Card Type")
              ($ :option {:value "player"} "Player")))
        ($ text-widget {:name "card-name"
                        :label "Card Name"
                        :field :name
                        :card card
                        :on-change update-card-field})
        (condp = (:type card)
          :player ($ player-fields {:card card :update-card-field update-card-field})))))
