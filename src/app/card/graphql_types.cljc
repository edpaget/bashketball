(ns app.card.graphql-types
  (:require
   [app.models :as models]
   [app.registry :as registry]))

(registry/defschema ::pagination-opts
  [:map
   [:limit {:optional true} :int]
   [:offset {:optional true} :int]])

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

;; --- AbilityCard ---
(registry/defschema ::ability-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:abilities {:optional true :default [""]} [:vector :string]]])

;; --- SplitPlayCard ---
(registry/defschema ::split-play-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:offense {:optional true :default ""} :string]
   [:defense {:optional true :default ""} :string]])

;; --- PlayCard ---
(registry/defschema ::play-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:play {:optional true :default ""} :string]])

;; --- CoachingCard ---
(registry/defschema ::coaching-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:coaching {:optional true :default ""} :string]])

;; --- StandardActionCard ---
(registry/defschema ::standard-action-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:offense {:optional true :default ""} :string]
   [:defense {:optional true :default ""} :string]])

;; --- TeamAssetCard ---
(registry/defschema ::team-asset-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:asset-power :string]])

;; =============================================================================
;; Field-Based Mutation Schemas
;; =============================================================================

;; Base input type for card identification
(registry/defschema ::card-identifier
  [:map
   [:name :string]
   [:version :string]])

;; Field-specific mutation input types
(registry/defschema ::update-game-asset-args
  [:map
   [:input ::card-identifier]
   [:game-asset-id [:maybe :uuid]]])

(registry/defschema ::update-deck-size-args
  [:map
   [:input ::card-identifier]
   [:deck-size :int]])

(registry/defschema ::update-sht-args
  [:map
   [:input ::card-identifier]
   [:sht :int]])

(registry/defschema ::update-pss-args
  [:map
   [:input ::card-identifier]
   [:pss :int]])

(registry/defschema ::update-def-args
  [:map
   [:input ::card-identifier]
   [:def :int]])

(registry/defschema ::update-speed-args
  [:map
   [:input ::card-identifier]
   [:speed :int]])

(registry/defschema ::update-size-args
  [:map
   [:input ::card-identifier]
   [:size ::models/PlayerSize]])

(registry/defschema ::update-abilities-args
  [:map
   [:input ::card-identifier]
   [:abilities [:vector :string]]])

(registry/defschema ::update-fate-args
  [:map
   [:input ::card-identifier]
   [:fate :int]])

(registry/defschema ::update-offense-args
  [:map
   [:input ::card-identifier]
   [:offense :string]])

(registry/defschema ::update-defense-args
  [:map
   [:input ::card-identifier]
   [:defense :string]])

(registry/defschema ::update-play-args
  [:map
   [:input ::card-identifier]
   [:play :string]])

(registry/defschema ::update-coaching-args
  [:map
   [:input ::card-identifier]
   [:coaching :string]])

(registry/defschema ::update-asset-power-args
  [:map
   [:input ::card-identifier]
   [:asset-power :string]])

