(ns app.card.edit
  (:require
   [app.models :as models]
   [app.card.types :as card-types]
   [uix.core :as uix :refer [defui $]]
   ["@headlessui/react" :as headless]))

(defn convert-to-blob
  [event update-field]
  (let [file (aget (.. event -target -files) 0)
        reader (js/FileReader.)]
    (set! (.-onload reader) #(update-field :img (.. % -target -result)))
    (.readAsDataURL reader file)))

(defui text-widget [{:keys [name label field card on-change input-type] :or {input-type "text"}}]
  (let [for-value (str name "-input")]
    ($ :div {:class "flex items-center mb-4"}
       ($ :label {:for for-value :class "w-32 text-sm font-medium text-gray-700 mr-2"} label)
       ($ :input {:value (get card field "")
                  :type input-type
                  :id for-value
                  :name name
                  :on-change #(on-change field (.. % -target -value))
                  :class "flex-grow mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"}))))

(defui multi-text [{:keys [name label field card on-change]}]
  (let [for-value (str name "-input")
        [widget-state set-widget-state!] (uix/use-state (field card [""]))]
    (uix/use-effect
     (fn []
       (on-change field widget-state))
     [on-change field widget-state])
    ($ :div {:key field :name name :class "mb-4"}
       ($ :label {:for for-value :class "block text-sm font-medium text-gray-700 mb-1"} label)
       ($ :div {:id for-value :class "mt-1 flex flex-col flex-grow"}
          (for [[idx item] (map-indexed vector widget-state)]
            ($ :span {:key (str "ability-" idx) :class "flex items-center mb-2"}
               ($ :textarea {:value item
                             :name for-value
                             :on-change #(set-widget-state! (assoc widget-state idx (.. % -target -value)))
                             :class "flex-grow mr-2 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"})
               ($ :button {:type "button"
                           :on-click #(set-widget-state! (into (subvec widget-state 0 idx)
                                                               (subvec widget-state (+ idx 1))))
                           :class "px-3 py-2 border border-red-500 text-red-500 rounded-md hover:bg-red-50 text-sm font-medium"}
                  "-")))
          ($ :button {:type "button"
                      :on-click #(set-widget-state! (conj widget-state ""))
                      :class "mt-2 px-3 py-2 border border-green-500 text-green-500 rounded-md hover:bg-green-50 text-sm font-medium self-start"}
             "+")))))

(defui card-fields [{:keys [card update-card-field]}]
  ($ :<>
     (for [field (get-in card-types/types [(:type card) :fields])
           :when (:auto-widget field)]
       (case (:input-type field)
         "select" ($ :div {:key (:field field) :class "flex items-center mb-4"}
                     ($ :label {:for (str (:name field) "-select") :class "w-32 text-sm font-medium text-gray-700 mr-2"} (:label field))
                     ($ :select {:name (:name field)
                                 :id (str (:name field) "-select")
                                 :on-change #(update-card-field (:field field) (.. % -target -value))
                                 :value (get card (:field field) "")
                                 :class "flex-grow mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"}
                        ($ :option {:value ""} (:select-label field))
                        (for [[option-value option-label] (:options field)]
                          ($ :option {:key option-value :value option-value} option-label))))
         "textarea" ($ :div {:key (:field field) :class "flex items-center mb-4"}
                       ($ :label {:for (str (:name field) "-textarea") :class "w-32 text-sm font-medium text-gray-700 mr-2"} (:label field))
                       ($ :textarea {:value (get card (:field field) "")
                                     :id (str (:name field) "-textarea")
                                     :name (:name field) ;; Corrected from :name (:name name)
                                     :on-change #(update-card-field (:field field) (.. % -target -value))
                                     :class "flex-grow mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"}))
         "multitext" ($ multi-text (merge {:key (:name field)
                                           :card card
                                           :on-change update-card-field}
                                          field))
         ($ text-widget (merge {:key (:name field)
                                :card card
                                :on-change update-card-field}
                               field))))))

(defui edit-card [{:keys [card update-card-field]}]
  (prn card)
  ($ :div {:class "p-6 bg-white shadow-lg rounded-lg max-w-2xl mx-auto my-8"}
     ($ :form {:class "space-y-6"}
        ($ :h1 {:class "text-3xl font-bold text-gray-900 mb-6 text-center"} "Edit Card")
        ($ headless/Field {:class "flex items-center mb-4"}
           ($ headless/Label {:class "w-32 text-sm font-medium text-gray-700 mr-2"} "Card Type")
           ($ headless/Select {:name "card-type"
                               :aria-label "Card type select"
                               :on-change #(update-card-field :type (keyword (.. % -target -value)))
                               :value (name (get card :card-type ""))
                               :class "flex-grow mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"}
              ($ :option {:value ""} "Select Card Type")
              (for [[type type-label] card-types/->type-label]
                ($ :option {:key (name type) :value (name type)} type-label))))
        ($ text-widget {:name "card-name"
                        :label "Card Name"
                        :field :name
                        :card card
                        :on-change update-card-field})
        ($ headless/Field {:class "flex items-center mb-4"}
           ($ headless/Label {:class "w-32 text-sm font-medium text-gray-700 mr-2"} "Card Image")
           ($ headless/Input {:type "file"
                              :accept "image/*"
                              :name "card-img"
                              :on-change #(convert-to-blob % update-card-field)
                              :class "flex-grow mt-1 block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-full file:border-0 file:text-sm file:font-semibold file:bg-indigo-50 file:text-indigo-700 hover:file:bg-indigo-100"}))
        ($ card-fields {:card card :update-card-field update-card-field}))))
