(ns app.models.card
  (:require [app.registry :refer [register-type!]]))

(register-type! :models/Card
                [:merge
                 ;; default fields
                 [:map {:pk [:card-type] :sk [:name :version] :type "card"}
                  [:name :string]
                  [:version {:default-value "0"} :string]
                  [:img-url {:ui/input-type "file"} :string]
                  [:created-at {:optional true
                                :dynamo/on-create true
                                :default-now true} :time/instant]
                  [:updated-at {:optional true
                                :default-now true} :time/instant]]
                 [:multi {:dispatch :card-type :graphql/union-type "card"}
                  ;; Player card
                  [1 [:map {:graphql/type "player-card"}
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
                       [:vector :string]]]]
                  [2 [:map {:graphql/type "ability-card"}
                      [:card-type [:= "ability"]]
                      [:abilities {:ui/label "Abilities"
                                   :ui/auto-widget true
                                   :default-value [""]}
                       [:vector :string]]]]
                  [3 [:merge
                      [:map
                       [:fate {:ui/label "Fate"
                               :ui/auto-widget true
                               :default-value 0}
                        :int]]
                      [:multi {:dispatch :card-type}
                       [1 [:map {:graphql/type "split-play-card"}
                           [:card-type [:= "split-play"]]
                           [:offense {:ui/label "Offense"
                                      :ui/auto-widget true
                                      :default-value ""}
                            :string]
                           [:defense {:ui/label "Defense"
                                      :ui/auto-widget true
                                      :default-value ""}
                            :string]]]
                       [2 [:map {:graphql/type "play-card"}
                           [:card-type [:= "play"]]
                           [:play {:ui/label "Play"
                                   :ui/auto-widget true
                                   :default-value ""}
                            :string]]]
                       [3 [:map {:graphql/type "coaching-card"}
                           [:card-type [:= "coaching"]]
                           [:coaching {:ui/label "Coaching"
                                       :ui/auto-widget true
                                       :default-value ""}
                            :string]]]
                       [4 [:map {:graphql/type "standard-action-card"}
                           [:card-type [:= "standard-action"]]
                           [:offense {:ui/label "Offense"
                                      :ui/auto-widget true
                                      :default-value ""}
                            :string]
                           [:defense {:ui/label "Defense"
                                      :ui/auto-widget true
                                      :default-value ""}
                            :string]]]
                       [5 [:map {:graphql/type "team-asset-card"}
                           [:card-type [:= "team-asset"]]
                           [:asset-power {:ui/label "Asset Power"
                                          :ui/auto-widget true
                                          :default-value ""}
                            :string]]]]]]]])
