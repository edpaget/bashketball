(ns app.card
  (:refer-clojure :exclude [list])
  (:require
   [app.asset :as asset]
   [app.db :as db]
   [app.models :as models]
   [app.graphql.resolvers :refer [defresolver alias-resolver]]
   [malli.experimental :as me]
   [app.registry :as registry]
   [app.graphql.compiler :as gql.compiler]
   [com.walmartlabs.lacinia.schema :as schema]
   [app.graphql.transformer :as gql.transformer]))

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
                     :from   [(models/->table-name ::models/GameCard)]
                     :where  [:and [:= :name name]
                              [:= :version version]]})))

(registry/defschema ::pagination-opts
  [:map
   [:limit {:optional true} :int]
   [:offset {:optional true} :int]])

(me/defn list :- ::models/GameCard
  "Lists game cards with pagination using HoneySQL. Relies on dynamic db binding. "
  ([pagination-opts :- ::pagination-opts]
   (list [:*] pagination-opts))
  ([cols :- [:vector :keyword]
    {:keys [limit offset] :or {limit 100 offset 0}} :- ::pagination-opts]
   (db/execute! {:select    cols
                 :from     [(models/->table-name ::models/GameCard)]
                 :order-by [:name :version]
                 :limit    limit
                 :offset   offset})))

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
                    :columns     (keys input)
                    :values      [(cond-> (update input :card-type db/->pg_enum)
                                    (:size input) (update :size db/->pg_enum)
                                    (:abilities input) (update :abilities #(conj [:lift] %))
                                    :always vals)]
                    :returning   [:*]}))

(me/defn update-db :- [:maybe ::models/GameCard]
  "Updates a GameCard in thedatabase by name and version."
  [card-name :- :string
   card-version :- :string
   input :- [:map-of :keyword :any]]
  (when (not-empty input)
    (db/execute-one! {:update    [(models/->table-name ::models/GameCard)]
                      :set       (cond-> input
                                   (:size input) (update :size db/->pg_enum)
                                   (:abilities input) (update :abilities #(conj [:lift] %)))
                      :where     [:and [:= :name card-name]
                                  [:= :version card-version]]
                      :returning [:*]})))

;; Schema for Query/card arguments
(registry/defschema ::card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]])

;; Schema for Query/cards arguments
;; app.card/list handles its own defaults for limit and offset if they are not provided.
(registry/defschema ::cards-args
  [:map
   [:limit {:optional true} [:maybe :int]]
   [:offset {:optional true} [:maybe :int]]])

(def ^:private tagger (gql.compiler/merge-tag-with-type ::models/GameCard))

(def ^:private game-card-tag-and-transform (juxt #(gql.transformer/encode % ::models/GameCard) tagger))
(def ^:private card-tag-and-transform (juxt #(gql.transformer/encode % ::models/Card) tagger))

;; --- Query Resolvers

(defresolver :Query/card
  "Retrieves a specific game card by its name and version."
  [:=> [:cat :any ::card-args :any]
   [:maybe ::models/GameCard]]
  [_context args _value]
  (some->> (get-by-name (:name args) (or (:version args) "0"))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

(defresolver :Query/cards
  "Retrieves a list of game cards with pagination."
  [:=> [:cat :any ::cards-args :any]
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
(registry/defschema ::player-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   ;; Fields specific to PlayerCard, defaults match ::models/PlayerCard
   [:deck-size {:optional true :default 5} :int]
   [:sht {:optional true :default 1} :int]
   [:pss {:optional true :default 1} :int]
   [:def {:optional true :default 1} :int]
   [:speed {:optional true :default 1} :int]
   [:size {:optional true :default :size-enum/SM} ::models/PlayerSize]
   [:abilities {:optional true :default [""]} [:vector :string]]])

(defresolver :Mutation/createPlayerCard
  "Create a PLAYER_CARD-typed card"
  [:=> [:cat :any ::player-card-args :any]
   ::models/PlayerCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/PLAYER_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(registry/defschema ::update-player-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:deck-size {:optional true} :int]
   [:sht {:optional true} :int]
   [:pss {:optional true} :int]
   [:def {:optional true} :int]
   [:speed {:optional true} :int]
   [:size {:optional true} ::models/PlayerSize]
   [:abilities {:optional true} [:vector :string]]])

(defresolver :Mutation/updatePlayerCard
  "Update a PLAYER_CARD-typed card"
  [:=> [:cat :any ::update-player-card-args :any]
   [:maybe ::models/PlayerCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- AbilityCard ---
(registry/defschema ::ability-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:abilities {:optional true :default [""]} [:vector :string]]])

(defresolver :Mutation/createAbilityCard
  "Create an ABILITY_CARD-typed card"
  [:=> [:cat :any ::ability-card-args :any]
   ::models/AbilityCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/ABILITY_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(registry/defschema ::update-ability-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:abilities {:optional true} [:vector :string]]])

(defresolver :Mutation/updateAbilityCard
  "Update an ABILITY_CARD-typed card"
  [:=> [:cat :any ::update-ability-card-args :any]
   [:maybe ::models/AbilityCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- SplitPlayCard ---
(registry/defschema ::split-play-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:offense {:optional true :default ""} :string]
   [:defense {:optional true :default ""} :string]])

(defresolver :Mutation/createSplitPlayCard
  "Create a SPLIT_PLAY_CARD-typed card"
  [:=> [:cat :any ::split-play-card-args :any]
   ::models/SplitPlayCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/SPLIT_PLAY_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(registry/defschema ::update-split-play-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true} :int]
   [:offense {:optional true} :string]
   [:defense {:optional true} :string]])

(defresolver :Mutation/updateSplitPlayCard
  "Update a SPLIT_PLAY_CARD-typed card"
  [:=> [:cat :any ::update-split-play-card-args :any]
   [:maybe ::models/SplitPlayCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- PlayCard ---
(registry/defschema ::play-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:play {:optional true :default ""} :string]])

(defresolver :Mutation/createPlayCard
  "Create a PLAY_CARD-typed card"
  [:=> [:cat :any ::play-card-args :any]
   ::models/PlayCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/PLAY_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(registry/defschema ::update-play-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true} :int]
   [:play {:optional true} :string]])

(defresolver :Mutation/updatePlayCard
  "Update a PLAY_CARD-typed card"
  [:=> [:cat :any ::update-play-card-args :any]
   [:maybe ::models/PlayCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- CoachingCard ---
(registry/defschema ::coaching-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:coaching {:optional true :default ""} :string]])

(defresolver :Mutation/createCoachingCard
  "Create a COACHING_CARD-typed card"
  [:=> [:cat :any ::coaching-card-args :any]
   ::models/CoachingCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/COACHING_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(registry/defschema ::update-coaching-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true} :int]
   [:coaching {:optional true} :string]])

(defresolver :Mutation/updateCoachingCard
  "Update a COACHING_CARD-typed card"
  [:=> [:cat :any ::update-coaching-card-args :any]
   [:maybe ::models/CoachingCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- StandardActionCard ---
(registry/defschema ::standard-action-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:offense {:optional true :default ""} :string]
   [:defense {:optional true :default ""} :string]])

(defresolver :Mutation/createStandardActionCard
  "Create a STANDARD_ACTION_CARD-typed card"
  [:=> [:cat :any ::standard-action-card-args :any]
   ::models/StandardActionCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/STANDARD_ACTION_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(registry/defschema ::update-standard-action-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true} :int]
   [:offense {:optional true} :string]
   [:defense {:optional true} :string]])

(defresolver :Mutation/updateStandardActionCard
  "Update a STANDARD_ACTION_CARD-typed card"
  [:=> [:cat :any ::update-standard-action-card-args :any]
   [:maybe ::models/StandardActionCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))

;; --- TeamAssetCard ---
(registry/defschema ::team-asset-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:asset-power :string]])

(defresolver :Mutation/createTeamAssetCard
  "Create a TEAM_ASSET_CARD-typed card"
  [:=> [:cat :any ::team-asset-card-args :any]
   ::models/TeamAssetCard]
  [_context args _value]
  (->> (create (assoc args :card-type :card-type-enum/TEAM_ASSET_CARD))
       game-card-tag-and-transform
       (apply schema/tag-with-type)))

(registry/defschema ::update-team-asset-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true} :int]
   [:asset-power {:optional true} :string]])

(defresolver :Mutation/updateTeamAssetCard
  "Update a TEAM_ASSET_CARD-typed card"
  [:=> [:cat :any ::update-team-asset-card-args :any]
   [:maybe ::models/TeamAssetCard]]
  [_context {card-name :name, card-version :version, :as args} _value]
  (some->> (update-db card-name card-version (dissoc args :name :version))
           game-card-tag-and-transform
           (apply schema/tag-with-type)))
