(ns app.card.reducer
  (:require [app.card.types :as card-types]))

(defn card-state-reducer
  [state {:keys [type field value new-state]}]
  (condp = type
    ::reset-state  new-state
    ::update-field (cond
                     (= field :card-type) (or (some->> (card-types/->field-defs {:card-type value})
                                                       (map (juxt :field :default))
                                                       (remove (comp nil? second))
                                                       (into {}))
                                              {:card-type :card-type-enum/INVALID})
                     :else (assoc state field value))))

(defn update-field-dispatch
  [dispatch]
  (fn [field value]
    (dispatch {:type ::update-field :field field :value value})))

(defn reset-state!
  [dispatch new-state]
  (dispatch {:type ::reset-state :new-state new-state}))
