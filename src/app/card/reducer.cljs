(ns app.card.reducer)

(defn card-state-reducer
  [state {:keys [type field value]}]
  (prn state)
  (prn type field value)
  (condp = type
    :update-field (assoc state field value)))

(defn update-field-dispatch
  [dispatch]
  (fn [field value]
    (dispatch {:type :update-field :field field :value value})))
