(ns app.card-e2e-test
  (:require
   [clojure.test :refer [deftest is testing use-fixtures]]
   [etaoin.api :as e]
   [e2e.fixtures :as fx]
   [app.models :as models]
   [app.db :as db]
   [app.test-utils :as tu]))

(use-fixtures :once fx/container-fixture fx/server-fixture)
(use-fixtures :each fx/webdriver-fixture)

(def login-script
  "fetch('/authn', {
     method: 'POST',
     headers: {'Content-Type': 'application/json'},
     body: JSON.stringify({action: 'login', 'id-token': 'test-user-token'})
   }).then(response => {
     if (!response.ok) {
       console.error('E2E Login POST failed:', response.status);
       throw new Error('Login POST failed');
     }
     window.location.reload();
   });")

(deftest ^:e2e card-edit-test
  (tu/with-inserted-data [::models/GameCard {:name "K-PAX"
                                             :version "0"
                                             :card-type (db/->pg_enum :card-type-enum/PLAYER_CARD)
                                             :deck-size 1
                                             :sht 1
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
                                             :asset-power nil}]
    (testing "user can edit a card and see the changes persist"
      (e/go fx/*driver* fx/*app-url*)

      (testing "Perform login to enable editing"
        (e/js-execute fx/*driver* login-script)
        (is (e/wait-visible fx/*driver* {:tag :button :fn/text "Logout"}) "Logout button should be visible after login"))

      (testing "Navigate to a card and edit a field"
        (e/go fx/*driver* (str fx/*app-url* "cards"))
        (is (e/wait-visible fx/*driver* {:tag :a :fn/text "K-PAX"}) "Card list should be visible")
        (e/click fx/*driver* {:tag :a :fn/text "K-PAX"})

        (is (e/wait-visible fx/*driver* {:tag :h1 :fn/text "Edit Card"}) "Edit form should be visible")

        (let [sht-input-selector {:tag :input :name "sht"}
              ;; This XPath finds the div with the stat value next to the "SHT" label in the display component
              sht-display-selector {:xpath "//p[text()='SHT']/following-sibling::p"}
              initial-sht-value "1"
              new-sht-value "5"]

          (testing "Verify initial card state"
            (is (= initial-sht-value (e/get-element-value fx/*driver* sht-input-selector)) "Initial SHT value in input is correct")
            (is (= initial-sht-value (e/get-element-text fx/*driver* sht-display-selector)) "Initial SHT value in display is correct"))

          (testing "Update the SHT value"
            (e/clear fx/*driver* sht-input-selector)
            (e/fill fx/*driver* sht-input-selector new-sht-value)
            ;; Wait for debounce (500ms) and auto-save to complete
            (e/wait 30))

          (testing "Verify updated value is reflected on the page"
            (is (e/wait-has-text fx/*driver* sht-display-selector new-sht-value) "Display SHT value should update after edit"))

          (testing "Verify updated value persists after page reload"
            (e/refresh fx/*driver*)
            (is (e/wait-visible fx/*driver* {:tag :h1 :fn/text "Edit Card"}) "Edit form should be visible after refresh")
            (is (= new-sht-value (e/get-element-value fx/*driver* sht-input-selector)) "Persisted SHT value in input is correct")
            (is (= new-sht-value (e/get-element-text fx/*driver* sht-display-selector)) "Persisted SHT value in display is correct")))))))
