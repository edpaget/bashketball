(ns app.card.reducer
  (:require
   [app.models :as models]
   [app.card.types :as card-types]
   [app.graphql.transformer :as gql.transformer]))

;; -------- Dispatch Methods -----------

(defn update-field-dispatch
  [dispatch]
  (fn [field value]
    (dispatch {:type ::update-field :field field :value value})))

(defn reset-state!
  [dispatch new-state]
  (dispatch {:type ::reset-state :new-state new-state}))

;; -------- Reducer Methods ------------

(defmulti card-state-field-reducer
  (fn [_ {:keys [field]}] field))

(defmethod card-state-field-reducer :card-type
  [_ {:keys [value]}]
  (or (some->> (card-types/->field-defs {:card-type value})
               (map (juxt :field :default))
               (remove (comp nil? second))
               (into {}))
      {:card-type :card-type-enum/INVALID}))

(defmethod card-state-field-reducer :fate
  [state {:keys [field value]}]
  (assoc state field (js/parseInt value)))

(defmethod card-state-field-reducer :default
  [state {:keys [field value]}]
  (assoc state field value))

(defn card-state-reducer
  [state {:keys [type new-state] :as action}]
  (condp = type
    ::reset-state new-state
    ::update-field (card-state-field-reducer state action)))
