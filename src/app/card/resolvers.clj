(ns app.card.resolvers
  (:refer-clojure :exclude [list])
  (:require
   [app.asset.resolvers :as asset]
   [app.card.graphql-types :as card-gql-types]
   [app.db :as db]
   [app.graphql.compiler :as gql.compiler]
   [app.graphql.resolvers :refer [defresolver alias-resolver]]
   [app.graphql.transformer :as gql.transformer]
   [app.models :as models]
   [com.walmartlabs.lacinia.schema :as schema]
   [malli.experimental :as me]))

(me/defn get-by-name :- ::models/GameCard
  "Retrieves a specific game card by its name and version using HoneySQL. Defaults to getting version 0 if unspecified."
  ([name :- :string]
   (get-by-name :* name "0"))
  ([name :- :string version :- :string]
   (get-by-name :* name version))
  ([cols :- [:vector :keyword]
    name :- :string
    version :- :string]
   (db/execute-one! {:select cols
                     :from [(models/->table-name ::models/GameCard)]
                     :where [:and [:= :name name]
                             [:= :version version]]})))

(me/defn list :- ::models/GameCard
  "Lists game cards with pagination using HoneySQL. Relies on dynamic db binding. "
  ([pagination-opts :- ::card-gql-types/pagination-opts]
   (list [:*] pagination-opts))
  ([cols :- [:vector :keyword]
    {:keys [limit offset] :or {limit 100 offset 0}} :- ::card-gql-types/pagination-opts]
   (db/execute! {:select cols
                 :from [(models/->table-name ::models/GameCard)]
                 :order-by [:name :version]
                 :limit limit
                 :offset offset})))

(me/defn set-game-asset-id :- :int
  [card-name :- :string
   card-version :- :string
   asset-or-id :- [:or :uuid ::models/GameAsset]]
  (db/execute-one! {:update [(models/->table-name ::models/GameCard)]
                    :set {:game-asset-id (if (map? asset-or-id) (:id asset-or-id) asset-or-id)}
                    :where [:and [:= :name card-name]
                            [:= :version card-version]]}))

(me/defn create :- ::models/GameCard
  "Save a GameCard model to the database"
  [input :- ::models/GameCard]
  (db/execute-one! {:insert-into [(models/->table-name ::models/GameCard)]
                    :columns (keys input)
                    :values [(cond-> (update input :card-type db/->pg_enum)
                               (:size input) (update :size db/->pg_enum)
                               (:abilities input) (update :abilities #(conj [:lift] %))
                               :always vals)]
                    :returning [:*]}))

(me/defn update-db :- [:maybe ::models/GameCard]
  "Updates a GameCard in thedatabase by name and version."
  [card-name :- :string
   card-version :- :string
   input :- [:map-of :keyword :any]]
  (when (not-empty input)
    (db/execute-one! {:update [(models/->table-name ::models/GameCard)]
                      :set (cond-> input
                             (:size input) (update :size db/->pg_enum)
                             (:abilities input) (update :abilities #(conj [:lift] %)))
                      :where [:and [:= :name card-name]
                              [:= :version card-version]]
                      :returning [:*]})))

(def ^:private tagger (gql.compiler/merge-tag-with-type ::models/GameCard))

(def ^:private game-card-tag-and-transform (juxt #(gql.transformer/encode % ::models/GameCard) tagger))
(def ^:private card-tag-and-transform (juxt #(gql.transformer/encode % ::models/Card) tagger))

;; --- Query Resolvers

(defresolver :Query/card
  "Retrieves a specific game card by its name and version."
  [:=> [:cat :any ::card-gql-types/card-args :any]
   [:maybe ::models/GameCard]]
  [_context args _value]
  (some->> (get-by-name (:name args) (or (:version args) "0"))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

(defresolver :Query/cards
  "Retrieves a list of game cards with pagination."
  [:=> [:cat :any ::card-gql-types/cards-args :any]
   [:vector ::models/Card]]
  [_context args _value]
  (->> (list args)
       (map card-tag-and-transform)
       (mapv (partial apply schema/tag-with-type))))

;; --- AssetFkResolver ---

(defresolver :PlayerCard/gameAsset
  "Retrieves the card's associated Game Asset"
  [:=> [:cat :any :any :any]
   [:maybe ::models/GameAsset]]
  [_context _args {:keys [gameAssetId]}]
  (when gameAssetId
    (gql.transformer/encode (asset/get-by-id gameAssetId) ::models/GameAsset)))

(alias-resolver :PlayerCard/gameAsset
                :AbilityCard/gameAsset
                :SplitPlayCard/gameAsset
                :PlayCard/gameAsset
                :CoachingCard/gameAsset
                :StandardActionCard/gameAsset
                :TeamAssetCard/gameAsset)

;; --- PlayerCard ---
(defresolver :Mutation/createPlayerCard
  "Create a PLAYER_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/player-card-args :any]
   ::models/PlayerCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/PLAYER_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(defresolver :Mutation/updatePlayerCard
  "Update a PLAYER_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/update-player-card-args :any]
   [:maybe ::models/PlayerCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- AbilityCard ---
(defresolver :Mutation/createAbilityCard
  "Create an ABILITY_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/ability-card-args :any]
   ::models/AbilityCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/ABILITY_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(defresolver :Mutation/updateAbilityCard
  "Update an ABILITY_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/update-ability-card-args :any]
   [:maybe ::models/AbilityCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- SplitPlayCard ---
(defresolver :Mutation/createSplitPlayCard
  "Create a SPLIT_PLAY_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/split-play-card-args :any]
   ::models/SplitPlayCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/SPLIT_PLAY_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(defresolver :Mutation/updateSplitPlayCard
  "Update a SPLIT_PLAY_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/update-split-play-card-args :any]
   [:maybe ::models/SplitPlayCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- PlayCard ---
(defresolver :Mutation/createPlayCard
  "Create a PLAY_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/play-card-args :any]
   ::models/PlayCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/PLAY_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(defresolver :Mutation/updatePlayCard
  "Update a PLAY_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/update-play-card-args :any]
   [:maybe ::models/PlayCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- CoachingCard ---
(defresolver :Mutation/createCoachingCard
  "Create a COACHING_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/coaching-card-args :any]
   ::models/CoachingCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/COACHING_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(defresolver :Mutation/updateCoachingCard
  "Update a COACHING_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/update-coaching-card-args :any]
   [:maybe ::models/CoachingCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- StandardActionCard ---
(defresolver :Mutation/createStandardActionCard
  "Create a STANDARD_ACTION_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/standard-action-card-args :any]
   ::models/StandardActionCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/STANDARD_ACTION_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(defresolver :Mutation/updateStandardActionCard
  "Update a STANDARD_ACTION_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/update-standard-action-card-args :any]
   [:maybe ::models/StandardActionCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- TeamAssetCard ---
(defresolver :Mutation/createTeamAssetCard
  "Create a TEAM_ASSET_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/team-asset-card-args :any]
   ::models/TeamAssetCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/TEAM_ASSET_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(defresolver :Mutation/updateTeamAssetCard
  "Update a TEAM_ASSET_CARD-typed card"
  [:=> [:cat :any ::card-gql-types/update-team-asset-card-args :any]
   [:maybe ::models/TeamAssetCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; =============================================================================
;; Field-Based Update Resolvers
;; =============================================================================

(defn- update-field-with-validation
  "Generic field update with card-type validation"
  [card-name card-version field-name field-value valid-card-types]
  (when-let [card (get-by-name card-name card-version)]
    (when (contains? valid-card-types (:card-type card))
      (update-db card-name card-version {field-name field-value}))))

(defresolver :Mutation/updateCardGameAsset
  "Update game-asset-id for any card type"
  [:=> [:cat :any ::card-gql-types/update-game-asset-args :any]
   [:maybe ::models/GameCard]]
  [_context {:keys [input game-asset-id]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :game-asset-id game-asset-id
              #{:card-type-enum/PLAYER_CARD :card-type-enum/ABILITY_CARD
                :card-type-enum/SPLIT_PLAY_CARD :card-type-enum/PLAY_CARD
                :card-type-enum/COACHING_CARD :card-type-enum/STANDARD_ACTION_CARD
                :card-type-enum/TEAM_ASSET_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardDeckSize
  "Update deck-size for PlayerCard only"
  [:=> [:cat :any ::card-gql-types/update-deck-size-args :any]
   [:maybe ::models/PlayerCard]]
  [_context {:keys [input deck-size]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :deck-size deck-size
              #{:card-type-enum/PLAYER_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardSht
  "Update sht field for PlayerCard only"
  [:=> [:cat :any ::card-gql-types/update-sht-args :any]
   [:maybe ::models/PlayerCard]]
  [_context {:keys [input sht]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :sht sht
              #{:card-type-enum/PLAYER_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardPss
  "Update pss field for PlayerCard only"
  [:=> [:cat :any ::card-gql-types/update-pss-args :any]
   [:maybe ::models/PlayerCard]]
  [_context {:keys [input pss]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :pss pss
              #{:card-type-enum/PLAYER_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardDef
  "Update def field for PlayerCard only"
  [:=> [:cat :any ::card-gql-types/update-def-args :any]
   [:maybe ::models/PlayerCard]]
  [_context {:keys [input def]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :def def
              #{:card-type-enum/PLAYER_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardSpeed
  "Update speed field for PlayerCard only"
  [:=> [:cat :any ::card-gql-types/update-speed-args :any]
   [:maybe ::models/PlayerCard]]
  [_context {:keys [input speed]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :speed speed
              #{:card-type-enum/PLAYER_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardSize
  "Update size field for PlayerCard only"
  [:=> [:cat :any ::card-gql-types/update-size-args :any]
   [:maybe ::models/PlayerCard]]
  [_context {:keys [input size]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :size size
              #{:card-type-enum/PLAYER_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardAbilities
  "Update abilities field for PlayerCard and AbilityCard"
  [:=> [:cat :any ::card-gql-types/update-abilities-args :any]
   [:maybe ::models/GameCard]]
  [_context {:keys [input abilities]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :abilities abilities
              #{:card-type-enum/PLAYER_CARD :card-type-enum/ABILITY_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardFate
  "Update fate field for fate-based cards"
  [:=> [:cat :any ::card-gql-types/update-fate-args :any]
   [:maybe ::models/GameCard]]
  [_context {:keys [input fate]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :fate fate
              #{:card-type-enum/SPLIT_PLAY_CARD :card-type-enum/PLAY_CARD
                :card-type-enum/COACHING_CARD :card-type-enum/STANDARD_ACTION_CARD
                :card-type-enum/TEAM_ASSET_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardOffense
  "Update offense field for SplitPlayCard and StandardActionCard"
  [:=> [:cat :any ::card-gql-types/update-offense-args :any]
   [:maybe ::models/GameCard]]
  [_context {:keys [input offense]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :offense offense
              #{:card-type-enum/SPLIT_PLAY_CARD :card-type-enum/STANDARD_ACTION_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardDefense
  "Update defense field for SplitPlayCard and StandardActionCard"
  [:=> [:cat :any ::card-gql-types/update-defense-args :any]
   [:maybe ::models/GameCard]]
  [_context {:keys [input defense]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :defense defense
              #{:card-type-enum/SPLIT_PLAY_CARD :card-type-enum/STANDARD_ACTION_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardPlay
  "Update play field for PlayCard only"
  [:=> [:cat :any ::card-gql-types/update-play-args :any]
   [:maybe ::models/PlayCard]]
  [_context {:keys [input play]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :play play
              #{:card-type-enum/PLAY_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardCoaching
  "Update coaching field for CoachingCard only"
  [:=> [:cat :any ::card-gql-types/update-coaching-args :any]
   [:maybe ::models/CoachingCard]]
  [_context {:keys [input coaching]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :coaching coaching
              #{:card-type-enum/COACHING_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

(defresolver :Mutation/updateCardAssetPower
  "Update asset-power field for TeamAssetCard only"
  [:=> [:cat :any ::card-gql-types/update-asset-power-args :any]
   [:maybe ::models/TeamAssetCard]]
  [_context {:keys [input asset-power]} _value]
  (let [{:keys [name version]} input]
    (some->> (update-field-with-validation
              name version :asset-power asset-power
              #{:card-type-enum/TEAM_ASSET_CARD})
             game-card-tag-and-transform
             (apply schema/tag-with-type))))

