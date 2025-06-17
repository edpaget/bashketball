(ns app.card-test
  (:require
   [clojure.test :refer [deftest is use-fixtures testing]]
   [app.card :as card]
   [app.db :as db]
   [app.models :as models]
   [app.test-utils :as tu]
   [app.graphql.resolvers :as gql.resolvers]
   [com.walmartlabs.lacinia.schema :as schema]))

(use-fixtures :once tu/db-fixture)
(use-fixtures :each tu/rollback-fixture)

(deftest get-by-name-test
  (testing "Retrieving an existing card"
    (let [card-data {:name "Test Player"
                     :version "0"
                     :card-type (db/->pg_enum :card-type-enum/PLAYER_CARD)
                     :deck-size 1
                     :sht 3
                     :pss 2
                     :def 1
                     :speed 4
                     :size (db/->pg_enum :size-enum/MD)
                     :abilities [:lift ["details"]]
                     :offense "Offense text"
                     :defense "Defense text"
                     :play nil
                     :coaching nil
                     :fate nil
                     :asset-power nil}
          ;; Prepare expected data (enums become keywords, JSONB needs reading)
          expected-card {:name "Test Player"
                         :version "0"
                         :card-type :card-type-enum/PLAYER_CARD
                         :deck-size 1
                         :sht 3
                         :pss 2
                         :def 1
                         :speed 4
                         :size  :size-enum/MD
                         :abilities ["details"]
                         :offense "Offense text"
                         :defense "Defense text"
                         :play nil
                         :coaching nil
                         :fate nil
                         :asset-power nil}]

      (tu/with-inserted-data [::models/GameCard card-data] ; Use the macro
        (testing "by name and version"
          (let [found-card (card/get-by-name "Test Player" "0")] ; Pass version as string
            (is (some? found-card))
            ;; Compare relevant fields, excluding auto-generated ones
            (is (= expected-card (select-keys found-card (keys expected-card))))))

        (testing "by name and version limiting columns returned"
          (let [found-card (card/get-by-name [:name :version] "Test Player" "0")] ; Pass version as string
            (is (some? found-card))
            ;; Compare relevant fields, excluding auto-generated ones
            (is (= (select-keys expected-card [:name :version]) found-card))))

        (testing "by name only (defaults to version 0)"
          (let [found-card (card/get-by-name "Test Player")] ; Defaults to version "0"
            (is (some? found-card))
            (is (= expected-card (select-keys found-card (keys expected-card)))))))))

  (testing "Retrieving a card with a specific version"
    (let [card-data {:name "Test Player V1"
                     :version "1" ; Version is TEXT
                     :card-type (db/->pg_enum :card-type-enum/PLAYER_CARD)
                     :deck-size 1}]
      (tu/with-inserted-data [::models/GameCard card-data]
        (let [found-card (card/get-by-name "Test Player V1" "1")] ; Pass version as string
          (is (some? found-card))
          (is (= "Test Player V1" (:name found-card)))
          (is (= "1" (:version found-card))))))) ; Compare version as string

  (testing "Retrieving a non-existent card"
    ;; No data insertion needed for this test
    (testing "by name and version"
      (is (nil? (card/get-by-name "NonExistent" "0"))))
    (testing "by name only"
      (is (nil? (card/get-by-name "NonExistent")))))

  (testing "Retrieving a card with existing name but non-existent version"
    (let [card-data {:name "VersionedTest"
                     :version "0" ; Version is TEXT
                     :card-type (db/->pg_enum :card-type-enum/PLAY_CARD)}]
      (tu/with-inserted-data [::models/GameCard card-data]
        (is (nil? (card/get-by-name "VersionedTest" "1"))))))) ; Check for non-existent string version

(deftest list-test
  (let [card1 {:name "Card A" :version "0" :card-type (db/->pg_enum :card-type-enum/PLAYER_CARD) :deck-size 1}
        card2 {:name "Card A" :version "1" :card-type (db/->pg_enum :card-type-enum/PLAY_CARD) :fate 1}
        card3 {:name "Card B" :version "0" :card-type (db/->pg_enum :card-type-enum/ABILITY_CARD) :abilities [:lift {:a "b"}]}
        card4 {:name "Card C" :version "0" :card-type (db/->pg_enum :card-type-enum/COACHING_CARD) :coaching "coach"}
        all-cards [card1 card2 card3 card4]]

    (testing "Listing cards with data"
      (tu/with-inserted-data [::models/GameCard card1
                              ::models/GameCard card2
                              ::models/GameCard card3
                              ::models/GameCard card4]
        (testing "Default limit and offset (limit 100, offset 0)"
          (let [keys-to-compare [:name :version]
                cards (card/list [:name :version] {})
                expected-cards all-cards]
            (is (= (count expected-cards) (count cards)))
            ;; Compare relevant fields, ignoring db-generated ones like created_at/updated_at
            (is (= (map #(select-keys % keys-to-compare) expected-cards)
                   cards))))

        (testing "With limit"
          (let [result (card/list {:limit 2})
                expected-cards (take 2 all-cards)]
            (is (= 2 (count result)))
            (is (= (map :name expected-cards)
                   (map :name result))))) ; Just check names for simplicity here

        (testing "With offset"
          (let [result (card/list {:offset 2})
                expected-cards (drop 2 all-cards)]
            (is (= 2 (count result)))
            (is (= (map :name expected-cards)
                   (map :name result)))))

        (testing "With limit and offset"
          (let [result (card/list {:limit 1 :offset 1})
                expected-cards [(second all-cards)]] ; Card A v1
            (is (= 1 (count result)))
            (is (= (map :name expected-cards)
                   (map :name result)))
            (is (= "1" (:version (first result)))))) ; Verify correct version

        (testing "With limit exceeding total"
          (let [result (card/list {:limit 10})
                expected-cards all-cards]
            (is (= 4 (count result)))
            (is (= (map :name expected-cards)
                   (map :name result)))))

        (testing "With offset exceeding total"
          (let [result (card/list {:offset 5})]
            (is (empty? (:cards result)))))))

    (testing "Listing when no cards exist"
      ;; No with-inserted-data needed here
      (let [result (into [] (card/list {}))]
        (is (empty? result))))))

(deftest create-test
  (testing "Creating a new player card with all fields"
    (let [input-card {:name "Creator Player" ; Unique name for this test
                      :version "0"
                      :card-type :card-type-enum/PLAYER_CARD
                      :deck-size 1
                      :sht 3
                      :pss 2
                      :def 1
                      :speed 4
                      :size :size-enum/LG ; Using a different size for variety
                      :abilities ["Power Hitter"]}
          created-card (card/create input-card)]
      (is (some? created-card) "Card should be created and returned")
      (is (some? (:created-at created-card)) "created_at should be set")
      (is (some? (:updated-at created-card)) "updated_at should be set")
      (is (= input-card (select-keys created-card (keys input-card))))

      (let [retrieved-card (card/get-by-name (:name input-card) (:version input-card))]
        (is (some? retrieved-card) "Card should be retrievable from DB")
        (is (= input-card (select-keys retrieved-card (keys input-card))))
        (is (some? (:created-at retrieved-card)))
        (is (some? (:updated-at retrieved-card))))))

  (testing "Creating a new ability card (without size and other player-specific fields)"
    (let [input-card {:name "New Ability Card Create"
                      :version "1"
                      :card-type :card-type-enum/ABILITY_CARD
                      :abilities ["Versatile"]}
          created-card (card/create input-card)]
      (is (some? created-card) "Card should be created and returned")
      (is (some? (:created-at created-card)) "created_at should be set")
      (is (some? (:updated-at created-card)) "updated_at should be set")
      (is (= input-card (select-keys created-card (keys input-card))))

      (let [retrieved-card (card/get-by-name (:name input-card) (:version input-card))]
        (is (some? retrieved-card) "Card should be retrievable from DB")
        (is (= input-card (select-keys retrieved-card (keys input-card))))
        (is (some? (:created-at retrieved-card)))
        (is (some? (:updated-at retrieved-card))))))

  (testing "Creating a card and ensuring all fields are present in returning *"
    ;; This test is to ensure that all columns defined in the schema are returned,
    ;; even if they are nil, and match their expected nil/default values.
    (let [input-card {:name "Minimal Coaching Card"
                      :version "c2"
                      :card-type :card-type-enum/COACHING_CARD
                      :deck-size 0
                      :coaching "Strategic Timeout"}
          ;; Define all fields expected in ::models/GameCard, setting to nil if not in input
          ;; This relies on knowing the full schema of GameCard.
          ;; For this example, I'll list the fields from the first test's input-card.
          all-expected-fields {:name "Minimal Coaching Card"
                               :version "c2"
                               :card-type :card-type-enum/COACHING_CARD
                               :deck-size 0
                               :sht nil
                               :pss nil
                               :def nil
                               :speed nil
                               :size nil
                               :abilities nil
                               :offense nil
                               :defense nil
                               :play nil
                               :coaching "Strategic Timeout"
                               :fate nil
                               :asset-power nil}
          created-card (card/create input-card)]
      (is (some? created-card))
      (is (= all-expected-fields (select-keys created-card (keys all-expected-fields))))

      ;; Verify by retrieving
      (let [retrieved-card (card/get-by-name (:name input-card) (:version input-card))]
        (is (some? retrieved-card))
        (is (= all-expected-fields (select-keys retrieved-card (keys all-expected-fields))))))))

(deftest query-card-resolver-test
  (testing "Query/card resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Query/card)
          card-data-v0 {:name "Resolver Test Card"
                        :version "0"
                        :card-type (db/->pg_enum :card-type-enum/PLAYER_CARD)
                        :deck-size 1
                        :offense "Offense for V0 Resolver Test Card"}
          card-data-v1 {:name "Resolver Test Card"
                        :version "1"
                        :card-type (db/->pg_enum :card-type-enum/PLAYER_CARD)
                        :deck-size 1
                        :offense "Offense for V1 Resolver Test Card"}
          ;; Expected data after retrieval (enums become keywords, select relevant fields)
          expected-card-v0 (-> card-data-v0
                               (assoc :deckSize 1)
                               (select-keys [:name :version :offense]))
          expected-card-v1 (-> card-data-v1
                               (assoc :deckSize 1)
                               (select-keys [:name :version :offense]))]

      (tu/with-inserted-data [::models/GameCard card-data-v0
                              ::models/GameCard card-data-v1]
        (testing "retrieves card by name and specific version \"0\""
          (let [result (resolver-fn nil {:name "Resolver Test Card" :version "0"} nil)]
            (is (some? result) "Result should not be nil")
            (is (= expected-card-v0 (select-keys result (keys expected-card-v0))))))

        (testing "retrieves card by name and defaults to version \"0\" when version argument is omitted"
          (let [result (resolver-fn nil {:name "Resolver Test Card"} nil)] ; :version is omitted
            (is (some? result) "Result should not be nil")
            (is (= expected-card-v0 (select-keys result (keys expected-card-v0))))))

        (testing "retrieves card by name and specific version \"1\""
          (let [result (resolver-fn nil {:name "Resolver Test Card" :version "1"} nil)]
            (is (some? result) "Result should not be nil")
            (is (= expected-card-v1 (select-keys result (keys expected-card-v1))))))

        (testing "returns nil if card name does not exist"
          (let [result (resolver-fn nil {:name "NonExistent Resolver Card" :version "0"} nil)]
            (is (nil? result))))

        (testing "returns nil if card version does not exist for the given name"
          (let [result (resolver-fn nil {:name "Resolver Test Card" :version "99"} nil)]
            (is (nil? result))))))))

(deftest create-card-mutations-test
  (testing "Mutation/createPlayerCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/createPlayerCard)
          base-valid-args {:name "Mutation Player Card"
                           :version "mpc0"
                           :deck-size 10
                           :sht 2
                           :pss 3
                           :def 1
                           :speed 5
                           :size :size-enum/SM
                           :abilities ["Fast Runner"]}]
      (testing "with valid arguments"
        (let [input-args base-valid-args
              expected-db-card (assoc input-args :card-type :card-type-enum/PLAYER_CARD)
              result (resolver-fn nil input-args nil)
              db-card (card/get-by-name (:name input-args) (:version input-args))]
          (is (some? result) "Result should not be nil")
          (is (= ::models/PlayerCard (::schema/type-name (meta #p result))) "Result should be tagged with Lacinia type")
          (is (= expected-db-card (select-keys result (keys expected-db-card))))
          (is (some? db-card))
          (is (= expected-db-card (select-keys db-card (keys expected-db-card))))))
      (testing "with invalid arguments"
        (testing "missing required 'name'"
          (is (= {:name ["missing required key"]
                  :message "failed to validate arguments"}
                 (get-in (resolver-fn nil (dissoc base-valid-args :name) nil)
                         [:resolved-value :data])))))))

  (testing "Mutation/createAbilityCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/createAbilityCard)
          input-args {:name "Mutation Ability Card"
                      :version "mac0"
                      :abilities ["Extra Power"]}
          expected-db-card (assoc input-args :card-type :card-type-enum/ABILITY_CARD)
          expected-gql-type ::models/AbilityCard
          result (resolver-fn nil input-args nil)
          db-card (card/get-by-name (:name input-args) (:version input-args))]
      (is (some? result) "Result should not be nil")
      (is (= expected-gql-type (::schema/type-name (meta result))) "Result should be tagged with Lacinia type")
      (is (= expected-db-card (select-keys result (keys expected-db-card))))
      ;; Verify card is in DB
      (is (some? db-card))
      (is (= expected-db-card (select-keys db-card (keys expected-db-card))))))

  (testing "Mutation/createSplitPlayCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/createSplitPlayCard)
          input-args {:name "Mutation SplitPlay Card"
                      :version "mspc0"
                      :fate 1
                      :offense "Offensive Play"
                      :defense "Defensive Play"}
          expected-db-card (assoc input-args :card-type :card-type-enum/SPLIT_PLAY_CARD)
          expected-gql-type ::models/SplitPlayCard
          result (resolver-fn nil input-args nil)
          db-card (card/get-by-name (:name input-args) (:version input-args))]
      (is (some? result) "Result should not be nil")
      (is (= expected-gql-type (::schema/type-name (meta result))) "Result should be tagged with Lacinia type")
      (is (= expected-db-card (select-keys result (keys expected-db-card))))
      ;; Verify card is in DB
      (is (some? db-card))
      (is (= expected-db-card (select-keys db-card (keys expected-db-card))))))

  (testing "Mutation/createPlayCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/createPlayCard)
          input-args {:name "Mutation Play Card"
                      :version "mpc1"
                      :fate 2
                      :play "Main Play Action"}
          expected-db-card (assoc input-args :card-type :card-type-enum/PLAY_CARD)
          expected-gql-type ::models/PlayCard
          result (resolver-fn nil input-args nil)
          db-card (card/get-by-name (:name input-args) (:version input-args))]
      (is (some? result) "Result should not be nil")
      (is (= expected-gql-type (::schema/type-name (meta result))) "Result should be tagged with Lacinia type")
      (is (= expected-db-card (select-keys result (keys expected-db-card))))
      ;; Verify card is in DB
      (is (some? db-card))
      (is (= expected-db-card (select-keys db-card (keys expected-db-card))))))

  (testing "Mutation/createCoachingCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/createCoachingCard)
          input-args {:name "Mutation Coaching Card"
                      :version "mcc0"
                      :fate 3
                      :coaching "Coaching Advice"}
          expected-db-card (assoc input-args :card-type :card-type-enum/COACHING_CARD)
          expected-gql-type ::models/CoachingCard
          result (resolver-fn nil input-args nil)
          db-card (card/get-by-name (:name input-args) (:version input-args))]
      (is (some? result) "Result should not be nil")
      (is (= expected-gql-type (::schema/type-name (meta result))) "Result should be tagged with Lacinia type")
      (is (= expected-db-card (select-keys result (keys expected-db-card))))
      ;; Verify card is in DB
      (is (some? db-card))
      (is (= expected-db-card (select-keys db-card (keys expected-db-card))))))

  (testing "Mutation/createStandardActionCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/createStandardActionCard)
          input-args {:name "Mutation Standard Action Card"
                      :version "msac0"
                      :fate 1
                      :offense "Standard Offense"
                      :defense "Standard Defense"}
          expected-db-card (assoc input-args :card-type :card-type-enum/STANDARD_ACTION_CARD)
          expected-gql-type ::models/StandardActionCard
          result (resolver-fn nil input-args nil) ; Corrected binding
          db-card (card/get-by-name (:name input-args) (:version input-args))]
      (is (some? result) "Result should not be nil")
      (is (= expected-gql-type (::schema/type-name (meta result))) "Result should be tagged with Lacinia type")
      (is (= expected-db-card (select-keys result (keys expected-db-card))))
      ;; Verify card is in DB
      (is (some? db-card))
      (is (= expected-db-card (select-keys db-card (keys expected-db-card))))))

  (testing "Mutation/createTeamAssetCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/createTeamAssetCard)
          input-args {:name "Mutation Team Asset Card"
                      :version "mtac0"
                      :fate 0
                      :asset-power "Team Power Boost"}
          expected-db-card (assoc input-args :card-type :card-type-enum/TEAM_ASSET_CARD)
          expected-gql-type ::models/TeamAssetCard
          result (resolver-fn nil input-args nil)
          db-card (card/get-by-name (:name input-args) (:version input-args))]
      (is (some? result) "Result should not be nil")
      (is (= expected-gql-type (::schema/type-name (meta result))) "Result should be tagged with Lacinia type")
      (is (= expected-db-card (select-keys result (keys expected-db-card))))
      ;; Verify card is in DB
      (is (some? db-card))
      (is (= expected-db-card (select-keys db-card (keys expected-db-card)))))))

(deftest set-game-asset-id-test
  (testing "Setting game_asset_id for a GameCard"
    (let [card-name "AssetLinkCard"
          card-version "alc0"
          ;; Base card data for insertion in each sub-test
          initial-card-data {:name card-name
                             :version card-version
                             :card-type (db/->pg_enum :card-type-enum/PLAY_CARD)
                             :fate 1}
          minimal-asset-data (fn [id] {:id id
                                       :mime-type "image/png" ; per ::models/GameAsset
                                       :img-url (str "https://example.com/assets/" id ".png") ; per ::models/GameAsset
                                       :status (db/->pg_enum :game-asset-status-enum/UPLOADED) ; per ::models/GameAsset
                                       ;; :error-message is optional
                                       ;; :created-at and :updated-at are typically set by DB
                                       })]

      (testing "successfully sets game_asset_id using a UUID"
        (let [asset-id (java.util.UUID/randomUUID)
              asset-data (minimal-asset-data asset-id)]
          (tu/with-inserted-data [::models/GameAsset asset-data
                                  ::models/GameCard initial-card-data]
            (let [update-count (card/set-game-asset-id card-name card-version asset-id)
                  updated-card (card/get-by-name card-name card-version)]
              (is (= {:next.jdbc/update-count 1} update-count) "Should update one row")
              (is (some? updated-card) "Updated card should be retrievable")
              (is (= asset-id (:game-asset-id updated-card)) "game_asset_id should be updated to the new UUID")))))

      (testing "successfully sets game_asset_id using a GameAsset map"
        (let [asset-id (java.util.UUID/randomUUID)
              ;; game-asset-map-for-db is created using the updated minimal-asset-data
              game-asset-map-for-db (minimal-asset-data asset-id)
              ;; The function under test expects a map conforming to ::models/GameAsset (keywords for enums)
              ;; when asset-or-id is a map.
              game-asset-map-for-fn {:id asset-id
                                     :mime-type "image/jpeg" ; Example, can differ from minimal-asset-data for test variety
                                     :img-url (str "https://example.com/other/" asset-id ".jpg")
                                     :status :game-asset-status-enum/PENDING
                                     ;; :error-message, :created-at, :updated-at are optional or auto-set
                                     }]
          ;; Ensure initial-card-data does not have game_asset_id for a clean test
          (tu/with-inserted-data [::models/GameAsset game-asset-map-for-db
                                  ::models/GameCard (dissoc initial-card-data :game-asset-id)]
            (let [update-count (card/set-game-asset-id card-name card-version game-asset-map-for-fn)
                  updated-card (card/get-by-name card-name card-version)]
              (is (= {:next.jdbc/update-count 1} update-count) "Should update one row")
              (is (some? updated-card) "Updated card should be retrievable")
              (is (= asset-id (:game-asset-id updated-card)) "game_asset_id should be updated using the ID from the map")))))

      (testing "updates an existing game_asset_id to a new UUID"
        (let [initial-asset-id (java.util.UUID/randomUUID)
              initial-asset-data (minimal-asset-data initial-asset-id)
              new-asset-id (java.util.UUID/randomUUID)
              new-asset-data (minimal-asset-data new-asset-id)
              card-with-initial-asset (assoc initial-card-data :game-asset-id initial-asset-id)]
          (tu/with-inserted-data [::models/GameAsset initial-asset-data
                                  ::models/GameAsset new-asset-data ; Also insert the new asset
                                  ::models/GameCard card-with-initial-asset]
            (let [update-count (card/set-game-asset-id card-name card-version new-asset-id)
                  updated-card (card/get-by-name card-name card-version)]
              (is (= {:next.jdbc/update-count 1} update-count) "Should update one row")
              (is (some? updated-card) "Updated card should be retrievable")
              (is (not= initial-asset-id (:game-asset-id updated-card)) "game_asset_id should have changed from the initial one")
              (is (= new-asset-id (:game-asset-id updated-card)) "game_asset_id should be updated to the new ID")))))

      (testing "returns 0 if card is not found"
        ;; No card inserted for this test case with this name/version
        (let [asset-id (java.util.UUID/randomUUID)
              update-count (card/set-game-asset-id "NonExistentCard" "v0" asset-id)]
          (is (= {:next.jdbc/update-count 0} update-count) "Should update zero rows as card does not exist"))))))

(deftest update-card-mutations-test
  (testing "Mutation/updatePlayerCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/updatePlayerCard)
          card-data {:name "Update Player Card"
                     :version "upc0"
                     :card-type (db/->pg_enum :card-type-enum/PLAYER_CARD)
                     :deck-size 10
                     :speed 5
                     :size (db/->pg_enum :size-enum/SM)}]
      (tu/with-inserted-data [::models/GameCard card-data]
        (testing "updates a single field"
          (let [update-args {:name "Update Player Card", :version "upc0", :speed 6}
                result (resolver-fn nil update-args nil)
                db-card (card/get-by-name (:name card-data) (:version card-data))]
            (is (some? result))
            (is (= 6 (:speed result)))
            (is (= 6 (:speed db-card)))
            (is (= 10 (:deck-size db-card)))))
        (testing "updates multiple fields"
          (let [update-args {:name "Update Player Card", :version "upc0", :deck-size 12, :size :size-enum/LG}
                result (resolver-fn nil update-args nil)
                db-card (card/get-by-name (:name card-data) (:version card-data))]
            (is (some? result))
            (is (= 12 (:deck-size result)))
            (is (= :size-enum/LG (:size result)))
            (is (= 12 (:deck-size db-card)))
            (is (= :size-enum/LG (:size db-card)))))
        (testing "returns nil for non-existent card"
          (is (nil? (resolver-fn nil {:name "Non-existent", :version "v0", :speed 10} nil))))
        (testing "is tagged with correct Lacinia type"
          (let [result (resolver-fn nil {:name "Update Player Card", :version "upc0", :speed 7} nil)]
            (is (= ::models/PlayerCard (::schema/type-name (meta result)))))))))

  (testing "Mutation/updateAbilityCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/updateAbilityCard)
          card-data {:name "Update Ability Card", :version "uac0", :card-type (db/->pg_enum :card-type-enum/ABILITY_CARD), :abilities [:lift ["Initial"]]}]
      (tu/with-inserted-data [::models/GameCard card-data]
        (let [update-args {:name "Update Ability Card", :version "uac0", :abilities ["Updated"]}
              result (resolver-fn nil update-args nil)
              db-card (card/get-by-name (:name card-data) (:version card-data))]
          (is (= ["Updated"] (:abilities result)))
          (is (= ["Updated"] (:abilities db-card)))
          (is (= ::models/AbilityCard (::schema/type-name (meta result))))))))

  (testing "Mutation/updateSplitPlayCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/updateSplitPlayCard)
          card-data {:name "Update SPC", :version "uspc0", :card-type (db/->pg_enum :card-type-enum/SPLIT_PLAY_CARD), :fate 1, :offense "O1", :defense "D1"}]
      (tu/with-inserted-data [::models/GameCard card-data]
        (let [update-args {:name "Update SPC", :version "uspc0", :fate 2, :offense "O2"}
              result (resolver-fn nil update-args nil)
              db-card (card/get-by-name (:name card-data) (:version card-data))]
          (is (= 2 (:fate result)))
          (is (= "O2" (:offense result)))
          (is (= "D1" (:defense db-card))) ; Unchanged
          (is (= ::models/SplitPlayCard (::schema/type-name (meta result))))))))

  (testing "Mutation/updatePlayCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/updatePlayCard)
          card-data {:name "Update PC", :version "upc1", :card-type (db/->pg_enum :card-type-enum/PLAY_CARD), :play "Initial"}]
      (tu/with-inserted-data [::models/GameCard card-data]
        (let [update-args {:name "Update PC", :version "upc1", :play "Updated"}
              result (resolver-fn nil update-args nil)
              db-card (card/get-by-name (:name card-data) (:version card-data))]
          (is (= "Updated" (:play result)))
          (is (= "Updated" (:play db-card)))
          (is (= ::models/PlayCard (::schema/type-name (meta result))))))))

  (testing "Mutation/updateCoachingCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/updateCoachingCard)
          card-data {:name "Update CC", :version "ucc0", :card-type (db/->pg_enum :card-type-enum/COACHING_CARD), :coaching "Initial"}]
      (tu/with-inserted-data [::models/GameCard card-data]
        (let [update-args {:name "Update CC", :version "ucc0", :coaching "Updated"}
              result (resolver-fn nil update-args nil)
              db-card (card/get-by-name (:name card-data) (:version card-data))]
          (is (= "Updated" (:coaching result)))
          (is (= "Updated" (:coaching db-card)))
          (is (= ::models/CoachingCard (::schema/type-name (meta result))))))))

  (testing "Mutation/updateStandardActionCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/updateStandardActionCard)
          card-data {:name "Update SAC", :version "usac0", :card-type (db/->pg_enum :card-type-enum/STANDARD_ACTION_CARD), :defense "Initial"}]
      (tu/with-inserted-data [::models/GameCard card-data]
        (let [update-args {:name "Update SAC", :version "usac0", :defense "Updated"}
              result (resolver-fn nil update-args nil)
              db-card (card/get-by-name (:name card-data) (:version card-data))]
          (is (= "Updated" (:defense result)))
          (is (= "Updated" (:defense db-card)))
          (is (= ::models/StandardActionCard (::schema/type-name (meta result))))))))

  (testing "Mutation/updateTeamAssetCard resolver"
    (let [resolver-fn (gql.resolvers/get-resolver-fn 'app.card :Mutation/updateTeamAssetCard)
          card-data {:name "Update TAC", :version "utac0", :card-type (db/->pg_enum :card-type-enum/TEAM_ASSET_CARD), :asset-power "Initial"}]
      (tu/with-inserted-data [::models/GameCard card-data]
        (let [update-args {:name "Update TAC", :version "utac0", :asset-power "Updated"}
              result (resolver-fn nil update-args nil)
              db-card (card/get-by-name (:name card-data) (:version card-data))]
          (is (= "Updated" (:asset-power result)))
          (is (= "Updated" (:asset-power db-card)))
          (is (= ::models/TeamAssetCard (::schema/type-name (meta result)))))))))
