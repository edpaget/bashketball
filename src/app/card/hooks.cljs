(ns app.card.hooks
  (:require
   [app.card.graphql-types :as card.gql-types]
   [app.graphql.client :as gql.client]
   [app.models :as models]
   [malli.core :as mc]
   [uix.core :as uix :refer [defhook]]))

;; === FIELD-BASED MUTATION SYSTEM ===

(def ^:private field-mutations
  "Maps field names to their corresponding field-based GraphQL mutations"
  {:game-asset-id :Mutation/updateCardGameAsset
   :deck-size :Mutation/updateCardDeckSize
   :name :Mutation/updateCardName
   :sht :Mutation/updateCardSht
   :pss :Mutation/updateCardPss
   :def :Mutation/updateCardDef
   :speed :Mutation/updateCardSpeed
   :size :Mutation/updateCardSize
   :abilities :Mutation/updateCardAbilities
   :fate :Mutation/updateCardFate
   :offense :Mutation/updateCardOffense
   :defense :Mutation/updateCardDefense
   :play :Mutation/updateCardPlay
   :coaching :Mutation/updateCardCoaching
   :asset-power :Mutation/updateCardAssetPower})

(def ^:private field-mutation-schemas
  "Maps field names to their argument schemas for field-based mutations"
  {:game-asset-id ::card.gql-types/update-game-asset-args
   :deck-size ::card.gql-types/update-deck-size-args
   :sht ::card.gql-types/update-sht-args
   :pss ::card.gql-types/update-pss-args
   :def ::card.gql-types/update-def-args
   :speed ::card.gql-types/update-speed-args
   :size ::card.gql-types/update-size-args
   :abilities ::card.gql-types/update-abilities-args
   :fate ::card.gql-types/update-fate-args
   :offense ::card.gql-types/update-offense-args
   :defense ::card.gql-types/update-defense-args
   :play ::card.gql-types/update-play-args
   :coaching ::card.gql-types/update-coaching-args
   :asset-power ::card.gql-types/update-asset-power-args})

(def ^:private ->create-mutation-name
  {:card-type-enum/PLAYER_CARD :Mutation/createPlayerCard
   :card-type-enum/ABILITY_CARD :Mutation/createAbilityCard
   :card-type-enum/SPLIT_PLAY_CARD :Mutation/createSplitPlayCard
   :card-type-enum/PLAY_CARD :Mutation/createPlayCard
   :card-type-enum/COACHING_CARD :Mutation/createCoachingCard
   :card-type-enum/STANDARD_ACTION_CARD :Mutation/createStandardActionCard
   :card-type-enum/TEAM_ASSET_CARD :Mutation/createTeamAssetCard})

(def ^:private ->create-mutation-args-schema
  {:card-type-enum/PLAYER_CARD ::card.gql-types/player-card-args
   :card-type-enum/ABILITY_CARD ::card.gql-types/ability-card-args
   :card-type-enum/SPLIT_PLAY_CARD ::card.gql-types/split-play-card-args
   :card-type-enum/PLAY_CARD ::card.gql-types/play-card-args
   :card-type-enum/COACHING_CARD ::card.gql-types/coaching-card-args
   :card-type-enum/STANDARD_ACTION_CARD ::card.gql-types/standard-action-card-args
   :card-type-enum/TEAM_ASSET_CARD ::card.gql-types/team-asset-card-args})

(defhook use-debounce [value delay]
  (let [[debounced-value set-debounced-value] (uix/use-state value)]
    (uix/use-effect
     (fn []
       (let [handler (js/setTimeout #(set-debounced-value value) delay)]
         #(.clearTimeout js/window handler)))
     [value delay])
    debounced-value))

(defhook use-card-create
  "Returns a graphql mutation hook [create-fn state] based on the type of card passed"
  [type]
  (let [mutation-name (->create-mutation-name type)
        model-type (models/->model-type type)
        mutation-args-schema (->create-mutation-args-schema type)
        mutation-args (->> mutation-args-schema mc/schema mc/deref mc/children (map first))]
    (gql.client/use-mutation
     {mutation-name (list* [model-type :app.graphql.compiler/all-fields]
                           mutation-args)}
     (name mutation-name)
     mutation-args-schema)))

(defhook use-card-query
  "Hook for loading a card by name and version"
  [card-name & [card-version]]
  (let [variables {:name card-name
                   :version (or card-version "0")}]
    (gql.client/use-query {:Query/card (list [::models/GameCard
                                              [::models/PlayerCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                              [::models/AbilityCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                              [::models/SplitPlayCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                              [::models/PlayCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                              [::models/CoachingCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                              [::models/StandardActionCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                                              [::models/TeamAssetCard :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]]
                                             :name)}
                          "getCardByName"
                          ::card.gql-types/card-args
                          variables)))

(defhook use-card-field-update
  "Returns mutation hook for updating a specific field.
   Returns [update-fn state] where update-fn takes card identifier and field value."
  [field-name]
  (let [mutation-name (field-mutations field-name)
        mutation-schema (field-mutation-schemas field-name)]
    (gql.client/use-mutation
     {mutation-name [::models/GameCard :app.graphql.compiler/all-fields
                     {:gameAsset [::models/GameAsset]}]}
     (name mutation-name)
     mutation-schema)))
