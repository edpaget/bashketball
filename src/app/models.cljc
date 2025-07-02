(ns app.models
  #?(:cljs
     (:require-macros
      [app.registry :as registry]))
  (:require
   [app.registry :as registry]
   [camel-snake-kebab.core :as csk]
   [malli.core :as mc]))

(registry/defschema ::IdentityStrategy
  [:enum {:graphql/type "IdentityStrategy"} :identity-strategy/SIGN_IN_WITH_GOOGLE :identity-strategy/INVALID])

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
  [:map {:graphql/interface "Card"
         ::pk [:name :version]}
   [:name :string]
   [:version {:default "0"} :string]
   [:game-asset-id {:ui/input-type "file"
                    ::fk [:maybe ::GameAsset]} [:maybe :uuid]]
   [:card-type {:graphql/hidden true} ::CardType]
   [:created-at :time/instant]
   [:updated-at :time/instant]])

(registry/defschema ::PlayerSize
  [:enum {:graphql/type "PlayerSize"}
   :size-enum/INVALID
   :size-enum/SM
   :size-enum/MD
   :size-enum/LG])

(registry/defschema ::PlayerCard
  [:merge ::Card
   [:map {:graphql/implements [::Card]
          :graphql/type "PlayerCard"}
    [:card-type {:default :card-type-enum/PLAYER_CARD}
     [:= :card-type-enum/PLAYER_CARD]]
    [:deck-size {:ui/label "Deck Size"
                 :ui/auto-widget true
                 :default 5}
     :int]
    [:sht {:ui/label "Shot"
           :ui/auto-widget true
           :default 1}
     :int]
    [:pss {:ui/label "Pass"
           :ui/auto-widget true
           :default 1}
     :int]
    [:def {:ui/label "Defense"
           :ui/auto-widget true
           :default 1}
     :int]
    [:speed {:ui/label "Speed"
             :ui/auto-widget true
             :default 1}
     :int]
    [:size {:ui/label "Size"
            :ui/select-label "Select Player Size"
            :ui/auto-widget true
            :ui/options {"SM" "Small"
                         "MD" "Medium"
                         "LG" "Large"}
            :default "SM"}
     ::PlayerSize]
    [:abilities {:ui/label "Abilities"
                 :ui/auto-widget true
                 :default [""]}
     [:vector :string]]]])

(registry/defschema ::AbilityCard
  [:merge ::Card
   [:map {:graphql/implements [::Card]
          :graphql/type "AbilityCard"}
    [:card-type {:default :card-type-enum/ABILITY_CARD}
     [:= :card-type-enum/ABILITY_CARD]]
    [:abilities {:ui/label "Abilities"
                 :ui/auto-widget true
                 :default [""]}
     [:vector :string]]]])

(registry/defschema ::CardWithFate
  [:merge ::Card
   [:map
    [:fate {:ui/label "Fate"
            :ui/auto-widget true
            :default 0}
     :int]]])

(registry/defschema ::SplitPlayCard
  [:merge ::CardWithFate
   [:map {:graphql/implements [::Card]
          :graphql/type "SplitPlayCard"}
    [:card-type {:default :card-type-enum/SPLIT_PLAY_CARD}
     [:= :card-type-enum/SPLIT_PLAY_CARD]]
    [:offense {:ui/label "Offense"
               :ui/auto-widget true
               :ui/input-type "textarea"
               :default ""}
     :string]
    [:defense {:ui/label "Defense"
               :ui/auto-widget true
               :ui/input-type "textarea"
               :default ""}
     :string]]])

(registry/defschema ::PlayCard
  [:merge ::CardWithFate
   [:map {:graphql/implements [::Card]
          :graphql/type "PlayCard"}
    [:card-type {:default :card-type-enum/PLAY_CARD}
     [:= :card-type-enum/PLAY_CARD]]
    [:play {:ui/label "Play"
            :ui/auto-widget true
            :ui/input-type "textarea"
            :default ""}
     :string]]])

(registry/defschema ::CoachingCard
  [:merge ::CardWithFate
   [:map {:graphql/implements [::Card]
          :graphql/type "CoachingCard"}
    [:card-type {:default :card-type-enum/COACHING_CARD}
     [:= :card-type-enum/COACHING_CARD]]
    [:coaching {:ui/label "Coaching"
                :ui/auto-widget true
                :ui/input-type "textarea"
                :default ""}
     :string]]])

(registry/defschema ::StandardActionCard
  [:merge ::CardWithFate
   [:map {:graphql/implements [::Card]
          :graphql/type "StandardActionCard"}
    [:card-type {:default :card-type-enum/STANDARD_ACTION_CARD}
     [:= :card-type-enum/STANDARD_ACTION_CARD]]
    [:offense {:ui/label "Offense"
               :ui/auto-widget true
               :ui/input-type "textarea"
               :default ""}
     :string]
    [:defense {:ui/label "Defense"
               :ui/auto-widget true
               :ui/input-type "textarea"
               :default ""}
     :string]]])

(registry/defschema ::TeamAssetCard
  [:merge ::CardWithFate
   [:map {:graphql/implements [::Card]
          :graphql/type "TeamAssetCard"}
    [:card-type {:default :card-type-enum/TEAM_ASSET_CARD}
     [:= :card-type-enum/TEAM_ASSET_CARD]]
    [:asset-power {:ui/label "Asset Power"
                   :ui/auto-widget true
                   :ui/input-type "textarea"} :string]]])

(registry/defschema ::GameCard
  [:multi {:dispatch :card-type
           :graphql/type "GameCard"
           ;; this could be derived from all members being 'card', but
           ;; let's just be explicit
           ::pk [:name :version]}
   [:card-type-enum/PLAYER_CARD ::PlayerCard]
   [:card-type-enum/ABILITY_CARD ::AbilityCard]
   [:card-type-enum/SPLIT_PLAY_CARD ::SplitPlayCard]
   [:card-type-enum/PLAY_CARD ::PlayCard]
   [:card-type-enum/COACHING_CARD ::CoachingCard]
   [:card-type-enum/STANDARD_ACTION_CARD ::StandardActionCard]
   [:card-type-enum/TEAM_ASSET_CARD ::TeamAssetCard]])

(registry/defschema ::GameAssetStatus
  [:enum {:graphql/type "GameAssetStatus"}
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
  ((-validator type) value))

(defn ->table-name
  [type]
  (or (-> type mc/deref mc/properties ::table_name)
      (-> type name csk/->snake_case_keyword)))

(defn ->pk
  "Return the primary key of the model as a vector"
  [type]
  (or (-> type mc/deref-recursive mc/properties ::pk) [:id]))

(defn ->set-lift
  "Return columns that need to be :lifted in honeysql as a set"
  [type]
  (let [schema (mc/deref-recursive type)]
    (condp = (mc/type schema)
      :multi (->> schema
                  mc/children
                  (map last)
                  (mapcat ->set-lift)
                  (into #{}))
      ;; default case for map, merge, etc.
      (if-let [entries (mc/children schema)]
        (do (prn entries)
          (->> entries
               (filter (fn [[_ _ child-schema]] (= :vector (mc/type child-schema))))
               (map first)
               (into #{})))
        #{}))))
