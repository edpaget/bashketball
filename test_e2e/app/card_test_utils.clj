(ns app.card-test-utils
  "Utilities for card e2e testing with comprehensive state management support"
  (:require
   [app.db :as db]
   [clojure.test :refer [is]]
   [clojure.tools.logging :as log]
   [e2e.fixtures :as fx]
   [etaoin.api :as e]
   [etaoin.keys :as e.keys]))

;;; Authentication Utilities

(def login-script
  "JavaScript to authenticate as test user"
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

(defn login-and-navigate
  "Login user and navigate to specified URL"
  [driver url]
  (e/go driver fx/*app-url*)
  (e/js-execute driver login-script)
  (is (e/wait-visible driver {:tag :button :fn/text "Logout"})
      "Logout button should be visible after login")
  (when url
    (e/go driver url)))

;;; Test Data Definitions

(def test-cards
  "Predefined test card data for various scenarios"
  {:basic-player {:name "Basic Player"
                  :version "0"
                  :card-type (db/->pg_enum :card-type-enum/PLAYER_CARD)
                  :deck-size 1
                  :sht 2 :pss 3 :def 2 :speed 4
                  :size (db/->pg_enum :size-enum/MD)
                  :abilities nil
                  :offense nil :defense nil :play nil :coaching nil :fate nil
                  :asset-power nil}

   :complex-player {:name "Complex Player"
                    :version "0"
                    :card-type (db/->pg_enum :card-type-enum/PLAYER_CARD)
                    :deck-size 1
                    :sht 5 :pss 4 :def 3 :speed 2
                    :size (db/->pg_enum :size-enum/LG)
                    :abilities [:lift ["Enhanced lift ability"]]
                    :offense "Powerful offense text"
                    :defense "Strong defense capability"
                    :play "Special play instructions"
                    :coaching "Team coaching notes"
                    :fate "Destiny description"
                    :asset-power nil}

   :ability-card {:name "Super Power"
                  :version "0"
                  :card-type (db/->pg_enum :card-type-enum/ABILITY_CARD)
                  :deck-size 1
                  :abilities [:teleport ["Instant movement capability"]]
                  :offense nil :defense nil :play nil :coaching nil :fate nil
                  :asset-power nil}

   :coaching-card {:name "Strategic Advantage"
                   :version "0"
                   :card-type (db/->pg_enum :card-type-enum/COACHING_CARD)
                   :deck-size 1
                   :coaching "Advanced coaching strategy"
                   :abilities nil
                   :offense nil :defense nil :play nil :fate nil
                   :asset-power nil}})

(defn create-test-card-data
  "Get test card data by key, optionally with overrides"
  ([card-key]
   (get test-cards card-key))
  ([card-key overrides]
   (merge (get test-cards card-key) overrides)))

;;; Field Interaction Utilities

(def field-selectors
  "Standard CSS selectors for card form fields"
  {:name {:tag :input :name "name"}
   :card-type {:tag :select :name "card-type"}
   :deck-size {:tag :input :name "deck-size"}
   :sht {:tag :input :name "sht"}
   :pss {:tag :input :name "pss"}
   :def {:tag :input :name "def"}
   :speed {:tag :input :name "speed"}
   :size {:tag :select :name "size"}
   :abilities {:tag :textarea :name "abilities"}
   :offense {:tag :textarea :name "offense"}
   :defense {:tag :textarea :name "defense"}
   :play {:tag :textarea :name "play"}
   :coaching {:tag :textarea :name "coaching"}
   :fate {:tag :textarea :name "fate"}})

(def state-selectors
  "Selectors for field state indicators"
  {:loading {:css "[data-loading='true']"}
   :error {:css "[data-error='true']"}
   :dirty {:css "[data-dirty='true']"}
   :conflict {:css "[data-conflict='true']"}
   :validating {:css "[data-validating='true']"}})

(def button-selectors
  "Selectors for action buttons"
  {:save {:css "[data-action='save']"}
   :revert {:css "[data-action='revert']"}
   :resolve-conflict {:css "[data-action='resolve-conflict']"}
   :resolve-local {:css "[data-action='resolve-local']"}
   :resolve-server {:css "[data-action='resolve-server']"}})

(defn fill-card-field
  "Fill a card form field with the given value"
  [driver field-name value]
  (log/debug "Filling field" field-name "with value" value)
  (let [selector (get field-selectors field-name)]
    (when-not selector
      (throw (ex-info (str "Unknown field: " field-name)
                      {:field field-name :available-fields (keys field-selectors)})))

    (cond
      ;; Handle select fields
      (= (:tag selector) :select)
      (e/select fx/*driver* selector value)

      ;; Handle text/number inputs and textareas
      :else
      (do
        (e/fill driver selector e.keys/backspace)
        (e/fill driver selector value)))))

(defn get-field-value
  "Get the current value of a card form field"
  [driver field-name]
  (let [selector (get field-selectors field-name)]
    (when-not selector
      (throw (ex-info (str "Unknown field: " field-name)
                      {:field field-name :available-fields (keys field-selectors)})))

    (cond
      ;; Handle select fields
      (= (:tag selector) :select)
      (e/get-element-text driver (assoc selector :css "option[selected]"))

      ;; Handle text/number inputs and textareas
      :else
      (e/get-element-value driver selector))))

;;; State Management Utilities

(defn wait-for-auto-save
  "Wait for auto-save to complete (default 750ms for debounce + save)"
  ([driver] (wait-for-auto-save driver 750))
  ([driver timeout-ms]
   (log/debug "Waiting for auto-save to complete")
   (e/wait (/ timeout-ms 1000))))

(defn assert-field-state
  "Assert that a field has the expected state indicators"
  [driver field-name expected-state]
  (let [field-selector (get field-selectors field-name)
        field-name-attr (name field-name)]
    (doseq [[state expected?] expected-state]
      (let [state-selector (merge (get state-selectors state)
                                  {:name field-name-attr})]
        (if expected?
          (is (e/exists? driver state-selector 2)
              (str "Field " field-name " should have " state " state"))
          (is (not (e/exists? driver state-selector 2))
              (str "Field " field-name " should not have " state " state")))))))

(defn wait-for-field-state
  "Wait for a field to reach a specific state"
  [driver field-name state-key should-have?]
  (let [field-name-attr (name field-name)
        state-selector (merge (get state-selectors state-key)
                              {:name field-name-attr})
        timeout 5]
    (if should-have?
      (e/wait-visible driver state-selector timeout)
      (e/wait-invisible driver state-selector timeout))))

;;; Card Creation & Management

(defn create-test-card
  "Create a new card through the UI with given data"
  [driver card-data]
  (log/debug "Creating test card with data:" card-data)

  ;; Navigate to new card page
  (e/go driver (str fx/*app-url* "cards/new"))
  (is (e/wait-visible driver {:tag :h1 :fn/text "Edit Card"})
      "New card form should be visible")

  ;; Fill required fields first
  (when (:name card-data)
    (fill-card-field driver :name (:name card-data)))

  (when (:card-type card-data)
    (fill-card-field driver :card-type (name (:card-type card-data))))

  (when (:deck-size card-data)
    (fill-card-field driver :deck-size (str (:deck-size card-data))))

  ;; Fill optional fields based on card type
  (doseq [[field value] (dissoc card-data :name :card-type :deck-size :version)]
    (when (and value (contains? field-selectors field))
      (fill-card-field driver field
                       (if (string? value)
                         value
                         (str value)))))

  ;; Wait for auto-save
  (wait-for-auto-save driver)

  ;; Return the card name for further operations
  (:name card-data))

(defn navigate-to-card
  "Navigate to a specific card's edit page"
  [driver card-name]
  (log/debug "Navigating to card:" card-name)
  (e/go driver (str fx/*app-url* "cards"))
  (is (e/wait-visible driver {:tag :a :fn/text card-name})
      (str "Card " card-name " should be visible in list"))
  (e/click driver {:tag :a :fn/text card-name})
  (is (e/wait-visible driver {:tag :h1 :fn/text "Edit Card"})
      "Edit form should be visible"))

(defn verify-card-persistence
  "Verify that card data persists correctly after page reload"
  [driver card-name expected-data]
  (log/debug "Verifying persistence for card:" card-name "with data:" expected-data)

  ;; Navigate to the card
  (navigate-to-card driver card-name)

  ;; Verify each field value
  (doseq [[field expected-value] expected-data]
    (when (contains? field-selectors field)
      (let [actual-value (get-field-value driver field)
            expected-str (str expected-value)]
        (is (= expected-str actual-value)
            (str "Field " field " should have value " expected-str
                 " but was " actual-value)))))

  ;; Reload page and verify again
  (e/refresh driver)
  (is (e/wait-visible driver {:tag :h1 :fn/text "Edit Card"})
      "Edit form should be visible after refresh")

  (doseq [[field expected-value] expected-data]
    (when (contains? field-selectors field)
      (let [actual-value (get-field-value driver field)
            expected-str (str expected-value)]
        (is (= expected-str actual-value)
            (str "Field " field " should persist value " expected-str
                 " after reload but was " actual-value))))))

;;; Display Component Utilities

(defn get-display-value
  "Get value from the card display component (as opposed to form input)"
  [driver field-name]
  (case field-name
    :sht (e/get-element-text driver {:xpath "//p[text()='SHT']/following-sibling::p"})
    :pss (e/get-element-text driver {:xpath "//p[text()='PSS']/following-sibling::p"})
    :def (e/get-element-text driver {:xpath "//p[text()='DEF']/following-sibling::p"})
    :speed (e/get-element-text driver {:xpath "//p[text()='SPEED']/following-sibling::p"})
    :size (e/get-element-text driver {:xpath "//p[text()='SIZE']/following-sibling::p"})
    :name (e/get-element-text driver {:xpath "//h1[contains(@class, 'card-name')]"})
    (throw (ex-info (str "No display selector for field: " field-name)
                    {:field field-name}))))

(defn verify-display-update
  "Verify that the display component shows the updated value"
  [driver field-name expected-value]
  (is (e/wait-has-text driver
                       (case field-name
                         :sht {:xpath "//p[text()='SHT']/following-sibling::p"}
                         :pss {:xpath "//p[text()='PSS']/following-sibling::p"}
                         :def {:xpath "//p[text()='DEF']/following-sibling::p"}
                         :speed {:xpath "//p[text()='SPEED']/following-sibling::p"}
                         :size {:xpath "//p[text()='SIZE']/following-sibling::p"})
                       (str expected-value))
      (str "Display should update to show " field-name " = " expected-value)))

;;; Validation & Error Testing

(defn trigger-validation-error
  "Trigger a validation error on a specific field"
  [driver field-name invalid-value]
  (fill-card-field driver field-name invalid-value)
  (wait-for-field-state driver field-name :error true))

(defn clear-validation-error
  "Clear a validation error by providing valid value"
  [driver field-name valid-value]
  (fill-card-field driver field-name valid-value)
  (wait-for-field-state driver field-name :error false))

;;; Navigation Utilities

(defn verify-card-in-list
  "Verify that a card appears in the cards list page"
  [driver card-name]
  (e/go driver (str fx/*app-url* "cards"))
  (is (e/wait-visible driver {:tag :a :fn/text card-name})
      (str "Card " card-name " should be visible in cards list")))

(defn get-cards-list
  "Get list of all card names from the cards index page"
  [driver]
  (e/go driver (str fx/*app-url* "cards"))
  (e/wait-visible driver {:css "a[href*='/cards/']"})
  (->> (e/query-all driver {:css "a[href*='/cards/']"})
       (map #(e/get-element-text driver %))
       (remove empty?)
       set))

;;; Performance & UX Testing

(defn measure-page-load-time
  "Measure time to load a page"
  [driver url]
  (let [start-time (System/currentTimeMillis)]
    (e/go driver url)
    (e/wait-visible driver {:tag :body})
    (- (System/currentTimeMillis) start-time)))

(defn measure-field-save-time
  "Measure time for field save feedback to appear"
  [driver field-name value]
  (let [start-time (System/currentTimeMillis)]
    (fill-card-field driver field-name value)
    ;; Wait for either loading state to appear and disappear, or just a brief period
    (try
      (wait-for-field-state driver field-name :loading true)
      (wait-for-field-state driver field-name :loading false)
      (catch Exception _
        ;; If no loading state appears, just wait briefly
        (e/wait 0.5)))
    (- (System/currentTimeMillis) start-time)))
