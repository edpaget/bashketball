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

(registry/defschema ::Card
  [:map {::pk [:name :version]}
   [:name :string]
   [:version {:default-value "0"} :string]
   [:img-url {:ui/input-type "file"} :string]
   [:card-type :string]
   [:created-at :time/instant]
   [:updated-at :time/instant]])

(registry/defschema ::PlayerCard
  [:merge ::Card
   [:map
    [:card-type [:= "player"]]
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
            :ui/options {"sm" "Small"
                         "md" "Medium"
                         "lg" "Large"}
            :default-value "sm"}
     [:enum "sm" "md" "lg"]]
    [:abilities {:ui/label "Abilities"
                 :ui/auto-widget true
                 :default-value [""]}
     [:vector :string]]]])

(registry/defschema ::AbilityCard
  [:merge ::Card
   [:map
    [:card-type [:= "ability"]]
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
    [:card-type [:= "split-play"]]
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
    [:card-type [:= "play"]]
    [:play {:ui/label "Defense"
            :ui/auto-widget true
            :default-value ""}
     :string]]])

(registry/defschema ::CoachingCard
  [:merge ::CardWithFate
   [:map
    [:card-type [:= "coaching"]]
    [:coaching {:ui/label "Defense"
                :ui/auto-widget true
                :default-value ""}
     :string]]])

(registry/defschema ::StandardActionCard
  [:merge ::CardWithFate
   [:map
    [:card-type [:= "standard-action"]]
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
    [:card-type [:= "team-asset"]]
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
