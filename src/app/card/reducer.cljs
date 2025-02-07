(ns app.card.reducer
  (:require [app.card.types :as card-types]))

(defn card-state-reducer
  [state {:keys [type field value]}]
  (condp = type
    :update-field (cond
                    (= field :type) (if-let [card-desc (value card-types/types)]
                                      (->> (:fields card-desc)
                                          (map #(vector (:field %) (:default %)))
                                          (into {}))
                                      {:type ""})
                    :else (assoc state field value))))

(defn update-field-dispatch
  [dispatch]
  (fn [field value]
    (dispatch {:type :update-field :field field :value value})))
