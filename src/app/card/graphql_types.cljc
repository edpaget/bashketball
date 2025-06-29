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

;; --- AbilityCard ---
(registry/defschema ::ability-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:abilities {:optional true :default [""]} [:vector :string]]])

(registry/defschema ::update-ability-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:abilities {:optional true} [:vector :string]]])

;; --- SplitPlayCard ---
(registry/defschema ::split-play-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:offense {:optional true :default ""} :string]
   [:defense {:optional true :default ""} :string]])

(registry/defschema ::update-split-play-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true} :int]
   [:offense {:optional true} :string]
   [:defense {:optional true} :string]])

;; --- PlayCard ---
(registry/defschema ::play-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:play {:optional true :default ""} :string]])

(registry/defschema ::update-play-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true} :int]
   [:play {:optional true} :string]])

;; --- CoachingCard ---
(registry/defschema ::coaching-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:coaching {:optional true :default ""} :string]])

(registry/defschema ::update-coaching-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true} :int]
   [:coaching {:optional true} :string]])

;; --- StandardActionCard ---
(registry/defschema ::standard-action-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:offense {:optional true :default ""} :string]
   [:defense {:optional true :default ""} :string]])

(registry/defschema ::update-standard-action-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true} :int]
   [:offense {:optional true} :string]
   [:defense {:optional true} :string]])

;; --- TeamAssetCard ---
(registry/defschema ::team-asset-card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true :default 0} :int]
   [:asset-power :string]])

(registry/defschema ::update-team-asset-card-args
  [:map
   [:name :string]
   [:version :string]
   [:game-asset-id {:optional true} [:maybe :uuid]]
   [:fate {:optional true} :int]
   [:asset-power {:optional true} :string]])
