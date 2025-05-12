(ns app.card-test
  (:require
   [clojure.test :refer [deftest is use-fixtures testing]]
   [app.card :as card]
   [app.db :as db]
   [app.models :as models]
   [app.test-utils :as tu]))

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
