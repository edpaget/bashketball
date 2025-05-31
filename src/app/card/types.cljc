(ns app.card.types)

(def ->type-label
  {:card-type-enum/PLAYER_CARD "Player"
   :card-type-enum/ABILITY_CARD "Ability"
   :card-type-enum/COACHING_CARD "Coaching"
   :card-type-enum/PLAY_CARD "Play"
   :card-type-enum/SPLIT_PLAY_CARD "Splity Play"
   :card-type-enum/STANDARD_ACTION_CARD "Standard Action"
   :card-type-enum/TEAM_ASSET_CARD "Team Asset"})

(def types
  {:player {:type-label "Player"
            :fields [{:name "card-type"
                      :label "Card Type"
                      :field :type
                      :default :player}
                     {:name "card-name"
                      :label "Card Name"
                      :field :name
                      :default "basic player"}
                     {:name "card-img"
                      :label "Card Image"
                      :field :img
                      :input-type "file"
                      :default nil}
                     {:name "deck-size"
                      :label "Deck Size"
                      :default 5
                      :input-type "number"
                      :auto-widget true
                      :field :deck-size}
                     {:name "sht"
                      :label "Shot"
                      :default 1
                      :input-type "number"
                      :auto-widget true
                      :field :sht}
                     {:name "pss"
                      :label "Pass"
                      :default 1
                      :input-type "number"
                      :auto-widget true
                      :field :pss}
                     {:name "def"
                      :label "Defense"
                      :default 1
                      :input-type "number"
                      :auto-widget true
                      :field :def}
                     {:name "speed"
                      :label "Speed"
                      :default 1
                      :input-type "number"
                      :auto-widget true
                      :field :speed}
                     {:name "size"
                      :label "Size"
                      :default "sm"
                      :input-type "select"
                      :auto-widget true
                      :field :size
                      :select-label "Select Player Size "
                      :options {"sm" "Small"
                                "md" "Medium"
                                "lg" "Large"}}
                     {:name "abilities"
                      :label "Abilities"
                      :field :abilities
                      :auto-widget true
                      :input-type "multitext"
                      :default [""]}]}
   :split-play {:type-label "Split Play"
                :fields [{:name "card-type"
                          :label "Card Type"
                          :field :type
                          :default :split-play}
                         {:name "card-name"
                          :label "Card Name"
                          :field :name
                          :default "offense / defense"}
                         {:name "card-img"
                          :label "Card Image"
                          :field :img
                          :input-type "file"
                          :default nil}
                         {:name "fate"
                          :label "Fate Value"
                          :field :fate
                          :auto-widget true
                          :input-type "number"
                          :default 0}
                         {:name "offense"
                          :label "Offense Ability"
                          :auto-widget true
                          :field :offense
                          :input-type "textarea"
                          :default ""}
                         {:name "defense"
                          :label "Defense Ability"
                          :auto-widget true
                          :input-type "textarea"
                          :field :defense
                          :default ""}]}
   :play {:type-label "Play"
          :fields [{:name "card-type"
                    :label "Card Type"
                    :field :type
                    :default :play}
                   {:name "card-name"
                    :label "Card Name"
                    :field :name
                    :default "offense / defense"}
                   {:name "card-img"
                    :label "Card Image"
                    :field :img
                    :input-type "file"
                    :default nil}
                   {:name "fate"
                    :label "Fate Value"
                    :auto-widget true
                    :field :fate
                    :input-type "number"
                    :default 0}
                   {:name "play"
                    :label "Play Ability"
                    :auto-widget true
                    :input-type "textarea"
                    :field :play
                    :default ""}]}
   :coaching {:type-label "Coaching"
              :fields [{:name "card-type"
                        :label "Card Type"
                        :field :type
                        :default :coaching}
                       {:name "card-name"
                        :label "Card Name"
                        :field :name
                        :default "offense / defense"}
                       {:name "card-img"
                        :label "Card Image"
                        :field :img
                        :input-type "file"
                        :default nil}
                       {:name "fate"
                        :label "Fate Value"
                        :field :fate
                        :auto-widget true
                        :input-type "number"
                        :default 0}
                       {:name "coaching"
                        :auto-widget true
                        :label "Coaching"
                        :field :coaching
                        :input-type "textarea"
                        :default ""}]}
   :standard-action {:type-label "Standard Action"
                     :fields [{:name "card-type"
                               :label "Card Type"
                               :field :type
                               :default :standard-action}
                              {:name "card-name"
                               :label "Card Name"
                               :field :name
                               :default "offense / defense"}
                              {:name "card-img"
                               :label "Card Image"
                               :field :img
                               :input-type "file"
                               :default nil}
                              {:name "fate"
                               :label "Fate Value"
                               :field :fate
                               :auto-widget true
                               :input-type "number"
                               :default 0}
                              {:name "offense"
                               :label "Offense Ability"
                               :field :offense
                               :auto-widget true
                               :input-type "textarea"
                               :default ""}
                              {:name "defense"
                               :label "Defense Ability"
                               :field :defense
                               :input-type "textarea"
                               :auto-widget true
                               :default ""}]}
   :ability {:type-label "Ability"
             :fields [{:name "card-type"
                       :label "Card Type"
                       :field :type
                       :default :ability}
                      {:name "card-name"
                       :label "Card Name"
                       :field :name
                       :default "offense / defense"}
                      {:name "card-img"
                       :label "Card Image"
                       :field :img
                       :input-type "file"
                       :default nil}
                      {:name "abilities"
                       :label "Abilities"
                       :field :abilities
                       :input-type "multitext"
                       :auto-widget true
                       :default [""]}]}
   :team-asset {:type-label "Team Asset"
                :fields [{:name "card-type"
                          :label "Card Type"
                          :field :type
                          :default :team-asset}
                         {:name "card-name"
                          :label "Card Name"
                          :field :name
                          :default "offense / defense"}
                         {:name "card-img"
                          :label "Card Image"
                          :field :img
                          :input-type "file"
                          :default nil}
                         {:name "fate"
                          :label "Fate Value"
                          :field :fate
                          :input-type "number"
                          :auto-widget true
                          :default 0}
                         {:name "asset-ability"
                          :label "Asset Power"
                          :field :asset-power
                          :input-type "textarea"
                          :auto-widget true
                          :default ""}]}})
