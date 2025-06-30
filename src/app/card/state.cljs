(ns app.card.state
  (:require
   [app.card.hooks :as card.hooks]
   [app.models :as models]
   [malli.core :as mc]
   [uix.core :as uix :refer [defhook]]))

(defn validate-card-field
  "Validate a specific field using the appropriate card schema"
  [card-type field-key field-value]
  (try
    (let [field-schema (case card-type
                         :card-type-enum/PLAYER_CARD ::models/PlayerCard
                         :card-type-enum/ABILITY_CARD ::models/AbilityCard
                         :card-type-enum/SPLIT_PLAY_CARD ::models/SplitPlayCard
                         :card-type-enum/PLAY_CARD ::models/PlayCard
                         :card-type-enum/COACHING_CARD ::models/CoachingCard
                         :card-type-enum/STANDARD_ACTION_CARD ::models/StandardActionCard
                         :card-type-enum/TEAM_ASSET_CARD ::models/TeamAssetCard
                         ::models/Card)
          schema-form (mc/form field-schema)
          field-def (get-in schema-form [2 field-key])]
      (if field-def
        (mc/validate [:map [field-key field-def]] {field-key field-value})
        ;; If field not found in schema, assume valid (for extensibility)
        true))
    (catch :default e
      ;; Log error and return false for validation failure
      (js/console.warn "Validation error for field" field-key ":" e)
      false)))

(defn get-validation-errors
  "Get detailed validation errors for a card"
  [card]
  (when card
    (let [card-type (:card-type card)
          field-schema (case card-type
                         :card-type-enum/PLAYER_CARD ::models/PlayerCard
                         :card-type-enum/ABILITY_CARD ::models/AbilityCard
                         :card-type-enum/SPLIT_PLAY_CARD ::models/SplitPlayCard
                         :card-type-enum/PLAY_CARD ::models/PlayCard
                         :card-type-enum/COACHING_CARD ::models/CoachingCard
                         :card-type-enum/STANDARD_ACTION_CARD ::models/StandardActionCard
                         :card-type-enum/TEAM_ASSET_CARD ::models/TeamAssetCard
                         ::models/Card)]
      (try
        (when-not (mc/validate field-schema card)
          (let [explain-data (mc/explain field-schema card)]
            ;; Convert malli errors to field-specific error map
            (reduce (fn [errors error]
                      (let [field-path (:path error)
                            field-key (first field-path)
                            message (:message error)]
                        (assoc errors field-key message)))
                    {}
                    (:errors explain-data))))
        (catch :default e
          (js/console.warn "Error getting validation errors:" e)
          {:general "Validation error occurred"})))))

(defn initial-card-state
  "Create initial state structure for card management"
  [card]
  {:card/data card
   :card/pristine card
   :card/dirty #{}
   :card/validating #{}
   :card/errors {}
   :card/loading #{}
   :card/optimistic {}
   :card/conflicts {}})

(defn card-state-reducer
  "Reducer for card state management with validation support"
  [state action]
  (case (:type action)
    :update-field
    (let [{:keys [field value]} action]
      (-> state
          (assoc-in [:card/data field] value)
          (update :card/dirty conj field)
          (update :card/errors dissoc field)))

    :reset-pristine
    (let [{:keys [card]} action]
      (-> state
          (assoc :card/data card)
          (assoc :card/pristine card)
          (assoc :card/dirty #{})
          (assoc :card/loading #{})
          (assoc :card/optimistic {})
          (assoc :card/errors {})
          (assoc :card/validating #{})
          (assoc :card/conflicts {})))

    :set-loading
    (let [{:keys [fields]} action]
      (assoc state :card/loading (set fields)))

    :set-errors
    (let [{:keys [errors]} action]
      (assoc state :card/errors errors))

    :set-validating
    (let [{:keys [fields]} action]
      (assoc state :card/validating (set fields)))

    :set-field-error
    (let [{:keys [field error]} action]
      (assoc-in state [:card/errors field] error))

    :clear-field-error
    (let [{:keys [field]} action]
      (update state :card/errors dissoc field))

    :clear-errors
    (assoc state :card/errors {})

    :validate-field
    (let [{:keys [field]} action]
      (update state :card/validating conj field))

    :set-conflicts
    (let [{:keys [conflicts]} action]
      (assoc state :card/conflicts conflicts))

    :resolve-conflict
    (let [{:keys [field resolution]} action]
      (-> state
          (update :card/conflicts dissoc field)
          (assoc-in [:card/data field] resolution)))

    state))

(defhook use-card-state
  "Unified card state management with automatic sync and validation"
  [initial-card & {:keys [auto-save? debounce-ms validate-on-change? validate-debounce-ms]
                   :or {auto-save? true
                        debounce-ms 500
                        validate-on-change? true
                        validate-debounce-ms 250}}]
  (let [[state dispatch] (uix/use-reducer card-state-reducer
                                          (initial-card-state initial-card))

        {:keys [card/errors card/dirty]} state

        ;; Extract current card data for debouncing
        current-card (:card/data state)
        debounced-card (card.hooks/use-debounce current-card debounce-ms)
        validation-card (card.hooks/use-debounce current-card validate-debounce-ms)

        ;; Get mutation hook for this card type
        ;; Get unified mutations for this card type
        mutations (card.hooks/use-card-mutations (:card-type current-card))
        {:keys [update]} mutations
        {:keys [data loading error]} (:state mutations)

        ;; Track initial mount and last synced state
        is-initial-mount (uix/use-ref true)
        last-synced-card (uix/use-ref initial-card)
        validation-cache (uix/use-ref {})]

    ;; Validation effect for changed fields
    (uix/use-effect
     (fn []
       (when validate-on-change?
         (let [dirty-fields dirty
               card-type (:card-type validation-card)]
           (when (seq dirty-fields)
             ;; Mark fields as validating
             (dispatch {:type :set-validating :fields dirty-fields})

             ;; Validate each dirty field
             (doseq [field dirty-fields]
               (let [field-value (get validation-card field)
                     cache-key [field field-value]]
                 ;; Check cache first to avoid redundant validation
                 (when-not (contains? @validation-cache cache-key)
                   (let [field-valid? (validate-card-field card-type field field-value)]
                     (swap! validation-cache assoc cache-key field-valid?)
                     (if field-valid?
                       (dispatch {:type :clear-field-error :field field})
                       (dispatch {:type :set-field-error
                                  :field field
                                  :error "Invalid value"})))))))

             ;; Clear validating state after short delay
           (js/setTimeout
            #(dispatch {:type :set-validating :fields #{}})
            100))))
     [validation-card validate-on-change? dirty])

    ;; Auto-save effect when auto-save is enabled
    (uix/use-effect
     (fn []
       (when auto-save?
         (let [card-to-compare (dissoc debounced-card :updated-at :created-at :game-asset :__typename)
               last-synced (dissoc @last-synced-card :updated-at :created-at :game-asset :__typename)]
           (cond
             @is-initial-mount
             (reset! is-initial-mount false)

             loading
             nil

             (= card-to-compare last-synced)
             nil

             ;; Don't save if there are validation errors
             (seq errors)
             nil

             :else
             (do
               (dispatch {:type :set-loading :fields (keys card-to-compare)})
               (update {:variables card-to-compare}))))))
     [debounced-card loading update auto-save? errors])

    ;; Handle server response
    (uix/use-effect
     (fn []
       (when-let [updated-card (-> data vals first)]
         (reset! last-synced-card updated-card)
         (dispatch {:type :reset-pristine :card updated-card})))
     [data])

    ;; Handle errors
    (uix/use-effect
     (fn []
       (when error
         (dispatch {:type :set-loading :fields #{}})
         (dispatch {:type :set-errors :errors {:general (str error)}})))
     [error])

    ;; Return comprehensive state and actions
    {:state state
     :update-field (fn [field value]
                     (dispatch {:type :update-field :field field :value value}))
     :validate-field (fn [field]
                       (dispatch {:type :validate-field :field field}))
     :clear-errors (fn []
                     (dispatch {:type :clear-errors}))
     :reset-card (fn [card]
                   (dispatch {:type :reset-pristine :card card}))
     :resolve-conflict (fn [field resolution]
                         (dispatch {:type :resolve-conflict :field field :resolution resolution}))
     :set-conflicts (fn [conflicts]
                      (dispatch {:type :set-conflicts :conflicts conflicts}))
     :is-dirty? (boolean (seq (:card/dirty state)))
     :is-loading? loading
     :is-validating? (boolean (seq (:card/validating state)))
     :has-errors? (boolean (seq (:card/errors state)))
     :has-conflicts? (boolean (seq (:card/conflicts state)))
     :errors (:card/errors state)
     :conflicts (:card/conflicts state)
     :card (:card/data state)}))

(defhook use-card-field
  "Field-level state management with validation, loading states, and conflict resolution"
  [card-state field-key]
  (let [{:keys [state update-field]} card-state
        value (get-in state [:card/data field-key])
        pristine-value (get-in state [:card/pristine field-key])
        is-dirty? (contains? (:card/dirty state) field-key)
        is-loading? (contains? (:card/loading state) field-key)
        is-validating? (contains? (:card/validating state) field-key)
        field-error (get-in state [:card/errors field-key])
        has-conflict? (contains? (:card/conflicts state) field-key)
        conflict-value (get-in state [:card/conflicts field-key])]

    {:value value
     :pristine-value pristine-value
     :update-value (fn [new-value] (update-field field-key new-value))
     :is-dirty? is-dirty?
     :is-loading? is-loading?
     :is-validating? is-validating?
     :has-error? (boolean field-error)
     :error field-error
     :has-conflict? has-conflict?
     :conflict-value conflict-value
     :resolve-conflict-with-local (fn []
                                    (when has-conflict?
                                      ((:resolve-conflict card-state) field-key value)))
     :resolve-conflict-with-server (fn []
                                     (when has-conflict?
                                       ((:resolve-conflict card-state) field-key conflict-value)))
     :revert-to-pristine (fn []
                           (update-field field-key pristine-value))}))

(defhook use-card-validation
  "Comprehensive card validation with detailed error reporting"
  [card]
  (let [validation-errors (get-validation-errors card)
        is-valid? (empty? validation-errors)]

    {:is-valid? is-valid?
     :errors validation-errors
     :validate-field (fn [field-key field-value]
                       (validate-card-field (:card-type card) field-key field-value))
     :get-field-error (fn [field-key]
                        (get validation-errors field-key))
     :has-field-error? (fn [field-key]
                         (contains? validation-errors field-key))}))

