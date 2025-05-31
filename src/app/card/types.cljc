(ns app.card.types
  (:require
   [app.models :as models]
   [malli.core :as mc]))

(def ->type-label
  {:card-type-enum/PLAYER_CARD "Player"
   :card-type-enum/ABILITY_CARD "Ability"
   :card-type-enum/COACHING_CARD "Coaching"
   :card-type-enum/PLAY_CARD "Play"
   :card-type-enum/SPLIT_PLAY_CARD "Splity Play"
   :card-type-enum/STANDARD_ACTION_CARD "Standard Action"
   :card-type-enum/TEAM_ASSET_CARD "Team Asset"})

(defn- ->input-type
  [schema]
  (case (mc/type schema)
    :vector "multitext"
    :enum "select"
    (:int :float) "number"
    "text"))

(defn- build-field-defs*
  [schema _ children _]
  (case (mc/type schema)
    ::mc/schema (mc/walk (mc/deref schema) build-field-defs*)
    :multi (into {} (map (juxt first last)) children)
    :map   (map (fn [[key {:keys [ui/input-type] :as properties} type]]
                  (cond-> (assoc properties :field key)
                    (nil? input-type) (assoc :ui/input-type (->input-type type))
                    ))
                children)
    :merge (mc/walk (mc/deref schema) build-field-defs*)
    schema))

(defn- build-field-defs
  [schema]
  (let [derefed (-> schema mc/schema mc/deref)
        dispatch-fn (-> derefed mc/properties :dispatch)
        field-map (mc/walk derefed build-field-defs*)]
    (fn [model]
      (get field-map (dispatch-fn model)))))

(def ->field-defs (build-field-defs ::models/GameCard))

(comment
  (app.card.types/->field-defs {:card-type :card-type-enum/PLAYER_CARD}))
