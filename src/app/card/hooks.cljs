(ns app.card.hooks
  (:require
   [app.models :as models]
   [app.graphql.client :as gql.client]
   [uix.core :as uix :refer [defhook]]))

(def ^:private ->mutation-name
  {:card-type-enum/PLAYER_CARD :Mutation/updatePlayerCard
   :card-type-enum/ABILITY_CARD :Mutation/updateAbilityCard
   :card-type-enum/SPLIT_PLAY_CARD :Mutation/updateSplitPlayCard
   :card-type-enum/PLAY_CARD :Mutation/updatePlayCard
   :card-type-enum/COACHING_CARD :Mutation/updateCoachingCard
   :card-type-enum/STANDARD_ACTION_CARD :Mutation/updateStandardActionCard
   :card-type-enum/TEAM_ASSET_CARD :Mutation/updateTeamAssetCard})

(def ^:private ->model-type
  {:card-type-enum/PLAYER_CARD ::models/PlayerCard
   :card-type-enum/ABILITY_CARD ::models/AbilityCard
   :card-type-enum/SPLIT_PLAY_CARD ::models/SplitPlayCard
   :card-type-enum/PLAY_CARD ::models/PlayCard
   :card-type-enum/COACHING_CARD ::models/CoachingCard
   :card-type-enum/STANDARD_ACTION_CARD ::models/StandardActionCard
   :card-type-enum/TEAM_ASSET_CARD ::models/TeamAssetCard})

(def ^:private ->update-mutation-vars
  {:card-type-enum/PLAYER_CARD
   [[:name :string]
    [:version :string]
    [:game-asset-id [:maybe :uuid]]
    [:deck-size [:maybe :int]]
    [:sht [:maybe :int]]
    [:pss [:maybe :int]]
    [:def [:maybe :int]]
    [:speed [:maybe :int]]
    [:size [:maybe :string]]
    [:abilities [:maybe [:vector :string]]]]
   :card-type-enum/ABILITY_CARD
   [[:name :string]
    [:version :string]
    [:game-asset-id [:maybe :uuid]]
    [:abilities [:maybe [:vector :string]]]]
   :card-type-enum/SPLIT_PLAY_CARD
   [[:name :string]
    [:version :string]
    [:game-asset-id [:maybe :uuid]]
    [:fate [:maybe :int]]
    [:offense [:maybe :string]]
    [:defense [:maybe :string]]]
   :card-type-enum/PLAY_CARD
   [[:name :string]
    [:version :string]
    [:game-asset-id [:maybe :uuid]]
    [:fate [:maybe :int]]
    [:play [:maybe :string]]]
   :card-type-enum/COACHING_CARD
   [[:name :string]
    [:version :string]
    [:game-asset-id [:maybe :uuid]]
    [:fate [:maybe :int]]
    [:coaching [:maybe :string]]]
   :card-type-enum/STANDARD_ACTION_CARD
   [[:name :string]
    [:version :string]
    [:game-asset-id [:maybe :uuid]]
    [:fate [:maybe :int]]
    [:offense [:maybe :string]]
    [:defense [:maybe :string]]]
   :card-type-enum/TEAM_ASSET_CARD
   [[:name :string]
    [:version :string]
    [:game-asset-id [:maybe :uuid]]
    [:fate [:maybe :int]]
    [:asset-power [:maybe :string]]]})

(defhook use-card-update
  "Returns a graphql mutation hook [update-fn state] based on the type of card passed"
  [type]
  (let [mutation-name (->mutation-name type)
        model-type (->model-type type)
        mutation-vars (->update-mutation-vars type)]
    (gql.client/use-mutation
     {mutation-name (list* [model-type :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                          (map first mutation-vars))}
     (name mutation-name)
     mutation-vars)))

(defhook use-debounce [value delay]
  (let [[debounced-value set-debounced-value] (uix/use-state value)]
    (uix/use-effect
     (fn []
       (let [handler (js/setTimeout #(set-debounced-value value) delay)]
         #(.clearTimeout js/window handler)))
     [value delay])
    debounced-value))
