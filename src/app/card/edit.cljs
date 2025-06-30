(ns app.card.edit
  (:require
   ["@headlessui/react" :as headless]

   [app.asset.uploader :as a.uploader]
   [app.card.types :as card-types]
   [uix.core :as uix :refer [defui $]]))

(defn- maybe-parse-int
  [input-type value]
  (when (not-empty value)
    (cond-> value (= input-type "number") js/parseInt)))

(defui text-widget [{:keys [ui/label field card on-change ui/input-type] :or {input-type "text"}}]
  ($ headless/Field {:class "flex items-center mb-4"}
     ($ headless/Label {:class "w-32 text-sm font-medium text-gray-700 mr-2"} label)
     ($ headless/Input {:value (get card field "")
                        :type input-type
                        :name (name field)
                        :on-change #(on-change field (maybe-parse-int input-type (.. % -target -value)))
                        :class "flex-grow mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"})))

(defui multi-text [{:keys [name ui/label field card on-change]}]
  (let [for-value (str name "-input")
        [widget-state set-widget-state!] (uix/use-state (field card [""]))]
    (uix/use-effect
     (fn []
       (on-change field widget-state))
     [on-change field widget-state])
    ($ headless/Field {:key field :name name :class "mb-4"}
       ($ headless/Label {:for for-value :class "block text-sm font-medium text-gray-700 mb-1"} label)
       ($ :div {:id for-value :class "mt-1 flex flex-col flex-grow"} ;; This div remains for structure
          (for [[idx item] (map-indexed vector widget-state)]
            ($ :span {:key (str "ability-" idx) :class "flex items-center mb-2"} ;; Span remains for layout
               ($ headless/Textarea {:value item
                                     :name for-value ;; Consider if this name needs to be unique per textarea if submitting as a traditional form
                                     :on-change #(set-widget-state! (assoc widget-state idx (.. % -target -value)))
                                     :class "flex-grow mr-2 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"})
               ($ headless/Button {:type "button"
                                   :on-click #(set-widget-state! (into (subvec widget-state 0 idx)
                                                                       (subvec widget-state (+ idx 1))))
                                   :class "px-3 py-2 border border-red-500 text-red-500 rounded-md hover:bg-red-50 text-sm font-medium"}
                  "-")))
          ($ headless/Button {:type "button"
                              :on-click #(set-widget-state! (conj widget-state ""))
                              :class "mt-2 px-3 py-2 border border-green-500 text-green-500 rounded-md hover:bg-green-50 text-sm font-medium self-start"}
             "+")))))

(defui card-fields [{:keys [card update-card-field]}]
  ($ :<>
     (for [field (card-types/->field-defs card)
           :when (:ui/auto-widget field)]
       (case (:ui/input-type field)
         "select" ($ headless/Field {:key (:field field) :class "flex items-center mb-4"}
                     ($ headless/Label {:class "w-32 text-sm font-medium text-gray-700 mr-2"} (:ui/label field))
                     ($ headless/Select {:name (:name field)
                                         :on-change #(update-card-field (:field field) (.. % -target -value))
                                         :value (name (get card (:field field) ""))
                                         :class "flex-grow mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"}
                        ($ :option {:value ""} (:ui/select-label field))
                        (for [[option-value option-label] (:ui/options field)]
                          ($ :option {:key option-value :value option-value} option-label))))
         "textarea" ($ headless/Field {:key (:field field) :class "flex items-center mb-4"}
                       ($ headless/Label {:class "w-32 text-sm font-medium text-gray-700 mr-2"} (:ui/label field))
                       ($ headless/Textarea {:value (get card (:field field) "")
                                             :name (:name field)
                                             :on-change #(update-card-field (:field field) (.. % -target -value))
                                             :class "flex-grow mt-1 block w-full px-3 py-2 border border-gray-300 rounded-md shadow-sm focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"}))
         "multitext" ($ multi-text (merge {:key (:field field)
                                           :card card
                                           :on-change update-card-field}
                                          field))
         ;; Default case, assuming text-widget handles other simple inputs
         ($ text-widget (merge {:key (:field field)
                                :card card
                                :on-change update-card-field}
                               field))))))

(defui edit-card [{:keys [card update-card-field new?]}]
  ($ :div {:class "p-6 bg-white shadow-lg rounded-lg max-w-2xl mx-auto my-8"}
     ($ :form {:class "space-y-6"}
        ($ :h1 {:class "text-3xl font-bold text-gray-900 mb-6 text-center"} (if new? "Create Card" "Edit Card"))
        ($ headless/Field {:class "flex items-center mb-4"}
           ($ headless/Label {:class "w-32 text-sm font-medium text-gray-700 mr-2"} "Card Type")
           (if new?
             ($ headless/Select {:name "card-type"
                                 :aria-label "Card type select"
                                 :on-change #(update-card-field :card-type (keyword :card-type-enum (.. % -target -value)))
                                 :value (name (get card :card-type ""))
                                 :class "flex-grow mt-1 block w-full pl-3 pr-10 py-2 text-base border-gray-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-md"}
                ($ :option {:value ""} "Select Card Type")
                (for [[type type-label] card-types/->type-label]
                  ($ :option {:key (name type) :value (name type)} type-label)))
             ($ :div {:class "flex-grow mt-1 block w-full px-3 py-2 text-gray-500 sm:text-sm bg-gray-100 rounded-md"}
                (get card-types/->type-label (get card :card-type)))))
        (if new?
          ($ :<>
             ($ text-widget {:ui/label "Card Name"
                             :field :name
                             :card card
                             :on-change update-card-field}))
          ($ :<>
             ($ headless/Field {:class "flex items-center mb-4"}
                ($ headless/Label {:class "w-32 text-sm font-medium text-gray-700 mr-2"} "Card Name")
                ($ :div {:class "flex-grow mt-1 block w-full px-3 py-2 text-gray-500 sm:text-sm bg-gray-100 rounded-md"}
                   (get card :name)))))
        ($ a.uploader/asset-upload {:update-card-field update-card-field})
        ($ card-fields {:card card :update-card-field update-card-field}))))
