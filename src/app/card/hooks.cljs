(ns app.card.hooks
  (:require
   [app.card.graphql-types :as card.gql-types]
   [app.graphql.client :as gql.client]
   [app.models :as models]
   [uix.core :as uix :refer [defhook]]
   [malli.core :as mc]))

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

(def ^:private ->update-mutation-args-schema
  {:card-type-enum/PLAYER_CARD ::card.gql-types/update-player-card-args
   :card-type-enum/ABILITY_CARD ::card.gql-types/update-ability-card-args
   :card-type-enum/SPLIT_PLAY_CARD ::card.gql-types/update-split-play-card-args
   :card-type-enum/PLAY_CARD ::card.gql-types/update-play-card-args
   :card-type-enum/COACHING_CARD ::card.gql-types/update-coaching-card-args
   :card-type-enum/STANDARD_ACTION_CARD ::card.gql-types/update-standard-action-card-args
   :card-type-enum/TEAM_ASSET_CARD ::card.gql-types/update-team-asset-card-args})

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

(defhook use-card-update
  "Returns a graphql mutation hook [update-fn state] based on the type of card passed"
  [type]
  (let [mutation-name (->mutation-name type)
        model-type (->model-type type)
        mutation-args-schema (->update-mutation-args-schema type)
        mutation-args (->> mutation-args-schema mc/schema mc/deref mc/children (map first))]
    (gql.client/use-mutation
     {mutation-name (list* [model-type :app.graphql.compiler/all-fields {:gameAsset [::models/GameAsset]}]
                           mutation-args)}
     (name mutation-name)
     mutation-args-schema)))

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
        model-type (->model-type type)
        mutation-args-schema (->create-mutation-args-schema type)
        mutation-args (->> mutation-args-schema mc/schema mc/deref mc/children (map first))]
    (gql.client/use-mutation
     {mutation-name (list* [model-type :app.graphql.compiler/all-fields]
                           mutation-args)}
     (name mutation-name)
     mutation-args-schema)))
