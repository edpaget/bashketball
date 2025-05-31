(ns app.card.reducer
  (:require [app.card.types :as card-types]))

(defn card-state-reducer
  [state {:keys [type field value]}]
  (condp = type
    :update-field (cond
                    (= field :card-type) (or  (some->> (card-types/->field-defs field)
                                                       (map #(vector (:field %) (:default %)))
                                                       (into {}))
                                              {:card-type :card-type-enum/INVALID})
                    :else (assoc state field value))))

(defn update-field-dispatch
  [dispatch]
  (fn [field value]
    (dispatch {:type :update-field :field field :value value})))
