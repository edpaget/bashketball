(ns app.card.show
  (:require [app.card.types :as card-types]
            [uix.core :as uix :refer [defui $]]))

(defui split-abilities [{:keys [offense defense]}]
  ($ :div {:class "grid grid-cols-2 gap-4 p-4 border-t border-gray-200"}
     ($ :div {:class "text-sm"}
        ($ :p {:class "font-semibold text-gray-700"} "Offense:")
        ($ :p {:class "text-gray-600"} offense))
     ($ :div {:class "text-sm"}
        ($ :p {:class "font-semibold text-gray-700"} "Defense:")
        ($ :p {:class "text-gray-600"} defense))))

(defui list-abilities [{:keys [abilities]}]
  ($ :div {:class "p-4 border-t border-gray-200 space-y-2"}
     (for [[idx ability] (map-indexed vector abilities)]
       ($ :div {:key (str "ability-idx-" idx) :class "text-sm text-gray-600 p-2 bg-gray-50 rounded"}
          ($ :p ability)))))

(defui show-card [{:keys [card-type] :as card}]
  ($ :div {:class "bg-white shadow-lg rounded-lg max-w-sm mx-auto overflow-hidden border border-gray-200 my-8"}
     ($ :div {:class "p-4 flex justify-between items-start border-b border-gray-200"}
        ($ :h1 {:class "text-xl font-bold text-gray-800"} (:name card))
        (case card-type
          :card-type-enum/ABILITY_CARD nil
          :card-type-enum/PLAYER_CARD ($ :small {:class "text-xs text-gray-500 self-center"} (str (:deck-size card) " cards"))
          ($ :small {:class "text-xs text-gray-500 self-center"} (str "Fate: " (:fate card)))))
     ($ :div {:class "relative"}
        (if-let [img (-> card :game-asset :img-url)]
          ($ :img {:src img :alt (:name card) :class "w-full h-48 object-cover"})
          ($ :div {:class "w-full h-48 bg-gray-200 flex items-center justify-center text-gray-400"}
             ($ :p "No Image")))
        ($ :h2 {:class "absolute bottom-0 left-0 bg-black bg-opacity-50 text-white text-lg font-semibold p-2 w-full text-center"}
           (card-types/->type-label card-type)))
     (case card-type
       :card-type-enum/PLAYER_CARD ($ :<>
                                      ($ :div {:class "p-4 grid grid-cols-3 gap-2 text-center border-t border-gray-200"}
                                         ($ :div {:class "text-sm"} ($ :p {:class "font-semibold text-gray-700"} "SHT")
                                            ($ :p {:class "text-gray-600"} (:sht card)))
                                         ($ :div {:class "text-sm"} ($ :p {:class "font-semibold text-gray-700"} "PSS")
                                            ($ :p {:class "text-gray-600"} (:pss card)))
                                         ($ :div {:class "text-sm"} ($ :p {:class "font-semibold text-gray-700"} "DEF")
                                            ($ :p {:class "text-gray-600"} (:def card))))
                                      ($ :div {:class "p-4 grid grid-cols-2 gap-2 text-center border-t border-gray-200"}
                                         ($ :div {:class "text-sm"} ($ :p {:class "font-semibold text-gray-700"} "SPEED")
                                            ($ :p {:class "text-gray-600"} (:speed card)))
                                         ($ :div {:class "text-sm"} ($ :p {:class "font-semibold text-gray-700"} "SIZE")
                                            ($ :p {:class "text-gray-600"}
                                               (name (:size card)))))
                                      ($ list-abilities card))
       :card-type-enum/COACHING_CARD ($ :div {:class "p-4 border-t border-gray-200"}
                                        ($ :p {:class "text-sm text-gray-600"} (:coaching card)))
       :card-type-enum/PLAY_CARD ($ :div {:class "p-4 border-t border-gray-200"}
                                    ($ :p {:class "text-sm text-gray-600"} (:play card)))
       :card-type-enum/TEAM_ASSET_CARD ($ :div {:class "p-4 border-t border-gray-200"}
                                          ($ :p {:class "text-sm text-gray-600"} (:asset-power card)))
       :card-type-enum/SPLIT_PLAY_CARD ($ split-abilities card)
       :card-type-enum/STANDARD_ACTION_CARD ($ split-abilities card)
       :card-type-enum/ABILITY_CARD ($ list-abilities card)
       ($ :p {:class "p-4 text-sm text-red-500"} "Unimplemented card type display"))))
