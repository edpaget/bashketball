(ns app.card.hooks
  (:require
   [app.card.graphql-types :as card.gql-types]
   [app.graphql.client :as gql.client]
   [app.models :as models]
   [malli.core :as mc]
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
                                             :name)} :card
                          ::card.gql-types/card-args
                          variables)))

(defhook use-card-mutations
  "Unified hook providing all card mutations for a specific card type.
   Returns map with :update, :create, :delete functions and shared state."
  [card-type]
  (let [;; Get mutation configurations
        update-mutation-name (->mutation-name card-type)
        create-mutation-name (->create-mutation-name card-type)
        model-type (->model-type card-type)
        update-args-schema (->update-mutation-args-schema card-type)
        create-args-schema (->create-mutation-args-schema card-type)

        ;; Extract argument lists
        update-args (->> update-args-schema mc/schema mc/deref mc/children (map first))
        create-args (->> create-args-schema mc/schema mc/deref mc/children (map first))

        ;; Create individual mutation hooks
        [update-fn update-state] (gql.client/use-mutation
                                  {update-mutation-name
                                   (list* [model-type :app.graphql.compiler/all-fields
                                           {:gameAsset [::models/GameAsset]}]
                                          update-args)}
                                  (name update-mutation-name)
                                  update-args-schema)

        [create-fn create-state] (gql.client/use-mutation
                                  {create-mutation-name
                                   (list* [model-type :app.graphql.compiler/all-fields]
                                          create-args)}
                                  (name create-mutation-name)
                                  create-args-schema)

        ;; Request deduplication cache
        pending-requests (uix/use-ref #{})]

    ;; Unified mutation interface
    {:update (fn [options]
               (let [request-key [:update (:variables options)]]
                 (when-not (contains? @pending-requests request-key)
                   (swap! pending-requests conj request-key)
                   (-> (update-fn options)
                       (.finally #(swap! pending-requests disj request-key))))))

     :create (fn [options]
               (let [request-key [:create (:variables options)]]
                 (when-not (contains? @pending-requests request-key)
                   (swap! pending-requests conj request-key)
                   (-> (create-fn options)
                       (.finally #(swap! pending-requests disj request-key))))))

     ;; Combined state information
     :state {:loading (or (:loading update-state) (:loading create-state))
             :error (or (:error update-state) (:error create-state))
             :data (or (:data update-state) (:data create-state))
             :update-state update-state
             :create-state create-state}

     ;; Utility functions
     :is-updating? (:loading update-state)
     :is-creating? (:loading create-state)
     :has-error? (boolean (or (:error update-state) (:error create-state)))
     :get-error (or (:error update-state) (:error create-state))}))
