(ns app.card.edit
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]))

(defn convert-to-blob
  [event update-field]
  (let [file (aget (.. event -target -files) 0)
        reader (js/FileReader.)]
    (set! (.-onload reader) #(update-field :img (.. % -target -result)))
    (.readAsDataURL reader file)))

(defui text-widget [{:keys [name label field card on-change input-type] :or {input-type "text"}}]
  (let [for-value (str name "-input")]
    ($ :div.widget
       ($ :label {:for for-value} label)
       ($ :input {:value (get card field "")
                  :type input-type
                  :id for-value
                  :name name
                  :on-change #(on-change field (.. % -target -value))}))))

(def player-field-desc [{:name "deck-size"
                         :label "Deck Size"
                         :field :deck-size}
                        {:name "sht"
                         :label "Shot"
                         :input-type "number"
                         :field :sht}
                        {:name "pss"
                         :label "Pass"
                         :input-type "number"
                         :field :pss}
                        {:name "def"
                         :label "Defense"
                         :input-type "number"
                         :field :def}
                        {:name "speed"
                         :label "Speed"
                         :input-type "number"
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
        ($ :div.widget
            ($ :label {:for "card-img-input"} "Card Image")
            ($ :input {:type "file"
                       :accept "image/*"
                       :id "card-img-input"
                       :name "card-img"
                       :on-change #(convert-to-blob % update-card-field)}))
        (condp = (:type card)
          :player ($ player-fields {:card card :update-card-field update-card-field})))))
