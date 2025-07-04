(ns app.card.state
  (:require
   [app.card.hooks :as card.hooks]
   [app.models :as models]
   [malli.core :as mc]
   [uix.core :as uix :refer [$ defhook defui]]))

(def ^:private card-provider (uix/create-context {}))

(defn validate-card
  "Validate a specific field using the appropriate card schema"
  [existing-errors {:keys [card/data]}]
  (let [{:keys [card-type] :as card-value} data
        schema (get models/->model-type card-type ::models/Card)]
    (merge existing-errors
           (when (models/validate schema card-value)
             (let [explain-data (mc/explain schema card-value)]
               (reduce (fn [errors error]
                         (let [field-path (:path error)
                               field-key (first field-path)
                               message (:message error)]
                           (assoc errors field-key message)))
                       {}
                       (:errors explain-data)))))))

(defn initial-card-state
  "Create initial state structure for card management"
  [card]
  {:card/data card
   :card/pristine card
   :card/dirty #{}
   :card/errors {}
   :card/loading #{}
   :card/loading-card? false
   :card/load-error nil})

(defn card-state-reducer
  "Reducer for card state management with validation and loading support"
  [state action]
  (case (:type action)
    :update-field
    (let [{:keys [field value]} action]
      (-> state
          (assoc-in [:card/data field] value)
          (update :card/dirty conj field)
          (update :card/errors dissoc field)
          (update :card/errors validate-card state)))

    :field-update-loading
    (let [{:keys [field loading?]} action]
      (if loading?
        (update state :card/loading conj field)
        (update state :card/loading disj field)))

    :field-update-success
    (let [{:keys [field updated-card]} action]
      (-> state
          (update :card/data merge updated-card)
          (update :card/dirty disj field)
          (update :card/loading disj field)
          (update :card/errors dissoc field)))

    :field-update-error
    (let [{:keys [field error]} action]
      (-> state
          (update :card/loading disj field)
          (assoc-in [:card/errors field] (str error))
          (assoc-in [:card/data field]
                    (get-in state [:card/pristine field]))))

    :reset-pristine
    (let [{:keys [card]} action]
      (-> state
          (assoc :card/data card)
          (assoc :card/pristine card)
          (assoc :card/dirty #{})
          (assoc :card/loading #{})
          (assoc :card/errors {})))

    :loading-card
    (-> state
        (assoc :card/loading-card? true)
        (assoc :card/load-error nil))

    :card-loaded
    (let [{:keys [card]} action]
      (-> state
          (assoc :card/data card)
          (assoc :card/pristine card)
          (assoc :card/dirty #{})
          (assoc :card/loading #{})
          (assoc :card/errors {})
          (assoc :card/loading-card? false)
          (assoc :card/load-error nil)))

    :card-load-error
    (let [{:keys [error]} action]
      (-> state
          (assoc :card/loading-card? false)
          (assoc :card/load-error error)))

    :set-loading
    (let [{:keys [fields]} action]
      (assoc state :card/loading (set fields)))

    :set-errors
    (let [{:keys [errors]} action]
      (assoc state :card/errors errors))

    :set-field-error
    (let [{:keys [field error]} action]
      (assoc-in state [:card/errors field] error))

    :clear-field-error
    (let [{:keys [field]} action]
      (update state :card/errors dissoc field))

    :clear-errors
    (assoc state :card/errors {})

    state))

(defhook use-card-state
  "Card state management for existing cards"
  [{:keys [name version debounce-ms] :or {debounce-ms 500}}]
  (let [[{:keys [card/data] :as state} dispatch] (uix/use-reducer card-state-reducer
                                                                  (initial-card-state {}))
        card-query-result (card.hooks/use-card-query name version)
        debounced-card (card.hooks/use-debounce data debounce-ms)
        last-auto-saved (uix/use-ref {})]

    ;; Card loading effect
    (uix/use-effect
     (fn []
       (let [{:keys [loading data error]} card-query-result]
         (cond
           loading
           (dispatch {:type :loading-card})

           error
           (dispatch {:type :card-load-error :error (str error)})

           data
           (when-let [loaded-card (:card data)]
             (dispatch {:type :card-loaded :card loaded-card})))))
     [card-query-result])

    {:state state
     :debounced-card debounced-card
     :dispatch dispatch
     :last-auto-saved last-auto-saved
     :update-field (uix/use-callback
                    (fn [field value]
                      (dispatch {:type :update-field :field field :value value}))
                    [])
     :clear-errors (uix/use-callback
                    (fn []
                      (dispatch {:type :clear-errors}))
                    [])
     :reset-card (uix/use-callback
                  (fn [card]
                    (dispatch {:type :reset-pristine :card card}))
                  [])
     :dirty? (boolean (seq (:card/dirty state)))
     :loading? (boolean (seq (:card/loading state)))
     :loading-card? (:card/loading-card? state)
     :has-errors? (boolean (seq (:card/errors state)))
     :errors (:card/errors state)
     :card-load-error (:card/load-error state)
     :card (:card/data state)}))

(defhook use-card-field
  "Field-level state management with validation and loading states"
  [field-key]
  (let [{:keys [state
                dispatch
                debounced-card
                last-auto-saved
                update-field]
         :as card-state} (uix/use-context card-provider)
        [save-field] (card.hooks/use-card-field-update field-key)
        value (get-in state [:card/data field-key])
        pristine-value (get-in state [:card/pristine field-key])
        field-error (get-in state [:card/errors field-key])
        dirty? (contains? (:card/dirty state) field-key)
        loading? (contains? (:card/loading state) field-key)
        errored? (boolean field-error)]

    (uix/use-effect
     (fn []
       (when (and dirty?
                  (not loading?)
                  (not errored?))
         (let [card-name (:name debounced-card)
               card-version (:version debounced-card)
               current-value (field-key debounced-card)
               last-saved-value (get @last-auto-saved field-key)]
           (when (not= current-value last-saved-value)
             (dispatch {:type :field-update-loading
                        :field field-key
                        :loading? true})
             (doto (save-field {:variables {:input {:name card-name
                                                    :version card-version}
                                            field-key current-value}})
               (.then #(do
                         (prn %)
                         (swap! last-auto-saved assoc field-key current-value)
                         (dispatch {:type :field-update-success
                                    :field field-key
                                    ;; TODO: Extract from correct operation name
                                    :updated-card (-> % :data vals first)})))
               (.catch #(dispatch {:type :field-update-error
                                   :field field-key
                                   :error %})))))))
     [dirty? loading? errored? debounced-card last-auto-saved dispatch field-key save-field])

    {:value value
     :pristine-value pristine-value
     :update-value (fn [new-value] (update-field field-key new-value))
     :dirty? dirty?
     :loading? loading?
     :has-error? (boolean field-error)
     :error field-error
     :revert-to-pristine (fn []
                           (update-field field-key pristine-value))}))

(defhook use-current-card []
  (-> (uix/use-context card-provider) :state :card/data))

(defui with-card
  [{:keys [name version children]
    :or {version 0}}]
  (let [card-state (use-card-state {:name name :version version})]
    ($ card-provider {:value card-state}
       children)))
