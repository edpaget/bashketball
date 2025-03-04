(ns app.models.card
  (:require [app.registry :refer [register-type!]]))

(register-type! :models/Card [:merge
   ;; default fields
   [:map {:pk [:card-type] :sk [:name :version] :type "card"}
    [:name :string]
    [:version {:default-value "0"} :string]
    [:img-url :string]]
   [:multi {:dispatch :card-type :graphql/union-type "card"}
    ;; Player card
    [1 [:map {:graphql/type "player-card"}
        [:card-type [:= "player"]]
        [:deck-size :int]
        [:sht :int]
        [:pss :int]
        [:def :int]
        [:speed :int]
        [:size [:enum "sm" "md" "lg"]]
        [:abilities [:vector :string]]]]
    [2 [:map {:graphql/type "ability-card"}
        [:card-type [:= "ability"]]
        [:abilities [:vector :string]]]]
    [3 [:merge
        [:map
         [:fate :int]]
        [:multi {:dispatch :card-type}
         [1 [:map {:graphql/type "split-play-card"}
             [:card-type [:= "split-play"]]
             [:offense :string]
             [:defense :string]]]
         [2 [:map {:graphql/type "play-card"}
             [:card-type [:= "play"]]
             [:play :string]]]
         [3 [:map {:graphql/type "coaching-card"}
             [:card-type [:= "coaching"]]
             [:coaching :string]]]
         [4 [:map {:graphql/type "standard-action-card"}
             [:card-type [:= "standard-action"]]
             [:defense :string]
             [:offense :string]]]
         [5 [:map {:graphql/type "team-asset-card"}
             [:card-type [:= "team-asset"]]
             [:asset-power :string]]]]]]]])
