(ns app.card-test
  (:require
   [clojure.test :refer [deftest is use-fixtures testing]]
   [app.card :as card]
   [app.db :as db]
   [app.models :as models]
   [app.test-utils :as tu]
   [app.graphql.resolvers :as gql-resolvers]))

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
    (let [resolver-fn (gql-resolvers/get-resolver-fn :Query/card)
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
                               (assoc :card-type :card-type-enum/PLAYER_CARD)
                               (select-keys [:name :version :card-type :deck-size :offense]))
          expected-card-v1 (-> card-data-v1
                               (assoc :card-type :card-type-enum/PLAYER_CARD)
                               (select-keys [:name :version :card-type :deck-size :offense]))]

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
