(ns app.models
  (:require
   [app.registry :as registry]))

(registry/defschema ::User
  [:map {:pk [:id] :type "user"}
   [:id :string]
   [:enrollment-state :string]
   [:username [:maybe :string]]
   [:created-at {:optional true
                 :dynamo/on-create true
                 :default-now true} :time/instant]
   [:updated-at {:optional true
                 :default-now true} :time/instant]])

(registry/defschema ::Session
  [:map {:pk [:id] :type "session"}
   [:id :uuid]
   [:user-id :string]
   [:created-at {:optional true
                 :dynamo/on-create true
                 :default-now true} :time/instant]
   [:expires-at [:maybe :time/instant]]])

(registry/defschema ::Card
  [:map {:pk [:card-type] :sk [:name :version] :type "card"}
   [:name :string]
   [:version {:default-value "0"} :string]
   [:img-url {:ui/input-type "file"} :string]
   [:card-type :string]
   [:created-at {:optional true
                 :dynamo/on-create true
                 :default-now true} :time/instant]
   [:updated-at {:optional true
                 :default-now true} :time/instant]])

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

