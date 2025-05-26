(ns app.models
  #?(:cljs
     (:require-macros
      [app.registry :as registry]))
  (:require
   [malli.core :as mc]
   [app.registry :as registry]
   [camel-snake-kebab.core :as csk]))

(registry/defschema ::IdentityStrategy
  [:enum :identity-strategy/SIGN_IN_WITH_GOOGLE :identity-strategy/INVALID])

(registry/defschema ::Identity
  [:map {::pk [:provider :provider_identity]}
   [:provider ::IdentityStrategy]
   [:provider_identity :string]
   [:email :string]
   [:created-at :time/instant]
   [:updated-at :time/instant]
   [:last-successful-at [:maybe :time/instant]]
   [:last-failed-at [:maybe :time/instant]]])

(registry/defschema ::Actor
  [:map
   [:id :string]
   [:enrollment-state :string]
   [:use-name [:maybe :string]]
   [:created-at :time/instant]
   [:updated-at :time/instant]])

(registry/defschema ::AppAuthorization
  [:map
   [:id :uuid]
   [:actor-id :string]
   [:provider ::IdentityStrategy]
   [:provider_identity :string]
   [:created-at :time/instant]
   [:expires-at [:maybe :time/instant]]])

(registry/defschema ::CardType
  [:enum
   :card-type-enum/INVALID
   :card-type-enum/PLAYER_CARD
   :card-type-enum/ABILITY_CARD
   :card-type-enum/SPLIT_PLAY_CARD
   :card-type-enum/PLAY_CARD
   :card-type-enum/COACHING_CARD
   :card-type-enum/STANDARD_ACTION_CARD
   :card-type-enum/TEAM_ASSET_CARD])

(registry/defschema ::Card
  [:map {::pk [:name :version]}
   [:name :string]
   [:version {:default-value "0"} :string]
   [:asset-id {:ui/input-type "file"} :string]
   [:card-type ::CardType]
   [:created-at :time/instant]
   [:updated-at :time/instant]])

(registry/defschema ::PlayerSize
  [:enum
   :size-enum/INVALID
   :size-enum/SM
   :size-enum/MD
   :size-enum/LG])

(registry/defschema ::PlayerCard
  [:merge ::Card
   [:map
    [:card-type [:= :card-type-enum/PLAYER_CARD]]
    [:deck-size {:ui/label "Deck Size"
                 :ui/auto-widget true
                 :default-value 5}
     :int]
    [:sht {:ui/label "Shot"
           :ui/auto-widget true
           :default-value 1}
     :int]
    [:pss {:ui/label "Pass"
           :ui/auto-widget true
           :default-value 1}
     :int]
    [:def {:ui/label "Defense"
           :ui/auto-widget true
           :default-value 1}
     :int]
    [:speed {:ui/label "Speed"
             :ui/auto-widget true
             :default-value 1}
     :int]
    [:size {:ui/label "Size"
            :ui/select-label "Select Player Size"
            :ui/auto-widget true
            :ui/options {"SM" "Small"
                         "MD" "Medium"
                         "LG" "Large"}
            :default-value "SM"}
     ::PlayerSize]
    [:abilities {:ui/label "Abilities"
                 :ui/auto-widget true
                 :default-value [""]}
     [:vector :string]]]])

(registry/defschema ::AbilityCard
  [:merge ::Card
   [:map
    [:card-type [:= :card-type-enum/ABILITY_CARD]]
    [:abilities {:ui/label "Abilities"
                 :ui/auto-widget true
                 :default-value [""]}
     [:vector :string]]]])

(registry/defschema ::CardWithFate
  [:merge ::Card
   [:map
    [:fate {:ui/label "Fate"
            :ui/auto-widget true
            :default-value 0}
     :int]]])

(registry/defschema ::SplitPlayCard
  [:merge ::CardWithFate
   [:map
    [:card-type [:= :card-type-enum/SPLIT_PLAY_CARD]]
    [:offense {:ui/label "Offense"
               :ui/auto-widget true
               :default-value ""}
     :string]
    [:defense {:ui/label "Defense"
               :ui/auto-widget true
               :default-value ""}
     :string]]])

(registry/defschema ::PlayCard
  [:merge ::CardWithFate
   [:map
    [:card-type [:= :card-type-enum/PLAY_CARD]]
    [:play {:ui/label "Play"
            :ui/auto-widget true
            :default-value ""}
     :string]]])

(registry/defschema ::CoachingCard
  [:merge ::CardWithFate
   [:map
    [:card-type [:= :card-type-enum/COACHING_CARD]]
    [:coaching {:ui/label "Defense"
                :ui/auto-widget true
                :default-value ""}
     :string]]])

(registry/defschema ::StandardActionCard
  [:merge ::CardWithFate
   [:map
    [:card-type [:= :card-type-enum/STANDARD_ACTION_CARD]]
    [:offense {:ui/label "Offense"
               :ui/auto-widget true
               :default-value ""}
     :string]
    [:defense {:ui/label "Defense"
               :ui/auto-widget true
               :default-value ""}
     :string]]])

(registry/defschema ::TeamAssetCard
  [:merge ::CardWithFate
   [:map
    [:card-type [:= :card-type-enum/TEAM_ASSET_CARD]]
    [:asset-power
     :string]]])

(registry/defschema ::GameCard
  [:multi {:dispatch :card-type
           ;; this could be derived from all members being 'card', but
           ;; let's just be explicit
           ::pk [:name :version]}
   [1 ::PlayerCard]
   [2 ::AbilityCard]
   [3 ::SplitPlayCard]
   [4 ::PlayCard]
   [5 ::CoachingCard]
   [6 ::StandardActionCard]
   [7 ::TeamAssetCard]])

(registry/defschema ::GameAssetStatus
  [:enum
   :game-asset-status-enum/PENDING
   :game-asset-status-enum/UPLOADED
   :game-asset-status-enum/ERROR])

(registry/defschema ::GameAsset
  [:map
   [:id :uuid]
   [:mime-type :string]
   [:img-url :string]
   [:status ::GameAssetStatus]
   [:error-message [:maybe :string]]
   [:updated-at :time/instant]
   [:created-at :time/instant]])

(def ^:private -validator
  (memoize (fn [type-name]
             (mc/validator type-name))))

(defn validate
  [type value]
  (prn type)
  ((-validator type) value))

(defn ->table-name
  [type]
  (or (-> type mc/deref mc/properties ::table_name)
      (-> type name csk/->snake_case_keyword)))

(defn ->pk
  "Return the primary key of the model as a vector"
  [type]
  (or (-> type mc/deref-recursive mc/properties ::pk) [:id]))
