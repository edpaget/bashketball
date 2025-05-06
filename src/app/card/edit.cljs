(ns app.card.edit
  (:require [app.card.types :as card-types]
            [uix.core :as uix :refer [defui $]]
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

(defui multi-text [{:keys [name label field card on-change]}]
  (let [for-value (str name "-input")
        [widget-state set-widget-state!] (uix/use-state (field card [""]))]
    (uix/use-effect
     (fn []
       (on-change field widget-state))
     [on-change field widget-state])
    ($ :div.widget {:key field :name name}
       ($ :label {:for for-value} label)
       ($ :div.multitext {:id for-value}
          (for [[idx item] (map-indexed vector widget-state)]
            ($ :span {:key (str "ability-" idx)}
               ($ :textarea {:value item
                             :name for-value
                             :on-change #(set-widget-state! (assoc widget-state idx (.. % -target -value)))})
               ($ :button {:type "button" :on-click #(set-widget-state! (into (subvec widget-state 0 idx)
                                                                              (subvec widget-state (+ idx 1))))}
                  "-")))
          ($ :button {:type "button" :on-click #(set-widget-state! (conj widget-state ""))} "+")))))

(defui card-fields [{:keys [card update-card-field]}]
  ($ :<>
     (for [field (get-in card-types/types [(:type card) :fields])
           :when (:auto-widget field)]
       (condp = (:input-type field)
         "select" ($ :div.widget {:key (:field field)}
                     ($ :label {:for (str (:name field) "-select")} (:label field))
                     ($ :select {:name (:name field)
                                 :id (str (:name field) "-select")
                                 :on-change #(update-card-field (:field field) (.. % -target -value))
                                 :value (get card (:field field) "")}
                        ($ :option {:value ""} (:select-label field))
                        (for [[option-value option-label] (:options field)]
                          ($ :option {:key option-value :value option-value} option-label))))
         "textarea" ($ :div.widget {:key (:field field)}
                     ($ :label {:for (str (:name field) "-textarea")} (:label field))
                     ($ :textarea {:value (get card (:field field) "")
                                   :id (str (:name field) "-textarea")
                                   :name (:name name)
                                   :on-change #(update-card-field (:field field) (.. % -target -value))}))
         "multitext" ($ multi-text (merge {:key (:name field)
                                           :card card
                                           :on-change update-card-field}
                                          field))
         ($ text-widget (merge {:key (:name field)
                                :card card
                                :on-change update-card-field}
                               field))))))

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
              (for [[type {:keys [type-label]}] card-types/types]
               ($ :option {:key (name type) :value (name type)} type-label))))
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
          ($ card-fields {:card card :update-card-field update-card-field}))))
