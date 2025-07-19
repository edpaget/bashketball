(ns app.card.state
  (:require
   [app.card.graphql-operations :as card.operations]
   [app.models :as models]
   [malli.core :as mc]
   [malli.error :as me]
   [uix.core :as uix :refer [$ defhook defui]]))

(def ^:private card-provider (uix/create-context {}))

(defn validate-card
  "Validate a specific field using the appropriate card schema"
  [{:keys [card/data] :as state}]
  (let [{:keys [card-type] :as card-value} data
        schema (get models/->model-type card-type ::models/Card)]
    (prn (-> (mc/explain schema card-value)
                 me/humanize))
    (assoc state :card/errors
           (when-not (models/validate schema card-value)
             (-> (mc/explain schema card-value)
                 me/humanize)))))

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
          validate-card))

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

    :card-creating
    (assoc state :card/creating? true :card/create-error nil)

    :card-created
    (let [{:keys [card]} action]
      (-> state
          (assoc :card/creating? false)
          (assoc :card/create-error nil)
          (assoc :card/data card)
          (assoc :card/pristine card)
          (assoc :card/dirty #{})
          (assoc :card/loading #{})))

    :card-create-error
    (let [{:keys [error]} action]
      (-> state
          (assoc :card/creating? false)
          (assoc :card/create-error error)))

    state))

(def ^:private non-autosaving-fields #{:card-type :name :game-asset})

(defhook use-card-state
  "Card state management for existing cards"
  [{:keys [name version debounce-ms new?] :or {debounce-ms 500
                                               new? false}}]
  (let [[{:keys [card/data] :as state} dispatch] (uix/use-reducer card-state-reducer
                                                                  (initial-card-state {}))
        debounced-card (card.operations/use-debounce data debounce-ms)
        last-auto-saved (uix/use-ref {})

        dirty (seq (:card/dirty state))
        loading? (boolean (seq (:card/loading state)))
        errored? (boolean (seq (:card/errors state)))]

    ;; Card loading effect
    (uix/use-effect
     (fn []
       (when-not new?
         (dispatch {:type :loading-card})
         (doto (card.operations/card-query name version)
           (.then #(when-let [loaded-card (get-in % [:data :card])]
                     (reset! last-auto-saved loaded-card)
                     (dispatch {:type :card-loaded :card loaded-card})))
           (.catch #(dispatch {:type :card-load-error :error (str %)})))))
     [name version new?])

    (uix/use-effect
     (fn []
       (when (and dirty
                  (not new?)
                  (not loading?)
                  (not errored?))
         (doseq [field dirty]
           (when-not (contains? non-autosaving-fields field)
             (let [card-name (:name debounced-card)
                   card-version (:version debounced-card)
                   current-value (field debounced-card)
                   last-saved-value (get @last-auto-saved field)]
               (when (not= current-value last-saved-value)
                 (dispatch {:type :field-update-loading
                            :field field
                            :loading? true})
                 (-> (card.operations/card-field-update field {:input {:name card-name
                                                                       :version card-version}
                                                               field current-value})
                     (.then #(do
                               (prn %)
                               (swap! last-auto-saved assoc field current-value)
                               (dispatch {:type :field-update-success
                                          :field field
                                          :updated-card (get-in % [:data (-> (card.operations/field-mutations field)
                                                                             clojure.core/name keyword)])})))
                     (.catch #(dispatch {:type :field-update-error
                                         :field field
                                         :error %})))))))))
     [dirty loading? errored? debounced-card new?])

    {:state state
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
     :create-card (uix/use-callback
                   (fn []
                     (when (and new? (not errored?))
                       (dispatch {:type :card-creating})
                       (let [[create-fn] (card.operations/card-create (:card-type data))]
                         (-> (create-fn {:variables data})
                             (.then #(do
                                       (dispatch {:type :card-created
                                                  :card (get-in % [:data])})
                                       %))
                             (.catch #(dispatch {:type :card-create-error :error %}))))))
                   [new? data errored?])
     :creating? (:card/creating? state)
     :create-error (:card/create-error state)
     :dirty? (boolean (seq (:card/dirty state)))
     :loading? (boolean (seq (:card/loading state)))
     :loading-card? (:card/loading-card? state)
     :new? new?
     :errored? (boolean (seq (:card/errors state)))
     :errors (:card/errors state)
     :card-load-error (:card/load-error state)
     :card (:card/data state)}))

(defhook use-card-field
  "Field-level state management with validation and loading states"
  [field-key]
  (let [{:keys [state update-field]} (uix/use-context card-provider)
        value (get-in state [:card/data field-key])
        pristine-value (get-in state [:card/pristine field-key])
        field-error (get-in state [:card/errors field-key])]

    {:value value
     :dirty? (contains? (:card/dirty state) field-key)
     :loading? (contains? (:card/loading state) field-key)
     :pristine-value pristine-value
     :update-value (fn [new-value] (update-field field-key new-value))
     :errored? (boolean field-error)
     :error field-error
     :revert-to-pristine (fn []
                           (update-field field-key pristine-value))}))

(defhook use-current-card []
  (let [ctx (uix/use-context card-provider)]
    {:current-card (-> ctx :state :card/data)
     :creating? (:creating? ctx)
     :create-error (:create-error ctx)
     :create-card (:create-card ctx)
     :new? (:new? ctx)}))

(defui with-card
  [{:keys [name version children new?]
    :or {version 0}}]
  (let [card-state (use-card-state {:name name :version version :new? new?})]
    ($ card-provider {:value card-state}
       children)))
