(ns app.card
  (:refer-clojure :exclude [list])
  (:require
   [app.db :as db]
   [app.models :as models]
   [app.graphql.resolvers :refer [defresolver]]
   [malli.experimental :as me]
   [app.registry :as registry]
   [app.graphql.compiler :as gql.compiler]
   [com.walmartlabs.lacinia.schema :as schema]
   [app.graphql.transformer :as gql.transformer]))

(me/defn get-by-name :- ::models/GameCard
  "Retrieves a specific game card by its name and version using HoneySQL. Defaults to getting version 0 if unspecified."
  ([name :- :string]
   (get-by-name :* name "0"))
  ([name :- :string version :- :string]
   (get-by-name :* name version))
  ([cols :- [:vector :keyword]
    name :- :string
    version :- :string]
   (db/execute-one! {:select cols
                     :from   [(models/->table-name ::models/GameCard)]
                     :where  [:and [:= :name name]
                              [:= :version version]]})))

(registry/defschema ::pagination-opts
  [:map
   [:limit {:optional true} :int]
   [:offset {:optional true} :int]])

(me/defn list :- ::models/GameCard
  "Lists game cards with pagination using HoneySQL. Relies on dynamic db binding. "
  ([pagination-opts :- ::pagination-opts]
   (list [:*] pagination-opts))
  ([cols :- [:vector :keyword]
    {:keys [limit offset] :or {limit 100 offset 0}} :- ::pagination-opts]
   (db/execute! {:select    cols
                 :from     [(models/->table-name ::models/GameCard)]
                 :order-by [:name :version]
                 :limit    limit
                 :offset   offset})))

(me/defn create :- ::models/GameCard
  "Save a GameCard model to the database"
  [input :- ::models/GameCard]
  (db/execute-one! {:insert-into [(models/->table-name ::models/GameCard)]
                    :columns     (keys input)
                    :values      [(cond-> (update input :card-type db/->pg_enum)
                                    (:size input) (update :size db/->pg_enum)
                                    (:abilities input) (update :abilities #(conj [:lift] %))
                                    :always vals)]
                    :returning   [:*]}))

;; Schema for Query/card arguments
(registry/defschema ::card-args
  [:map
   [:name :string]
   [:version {:optional true :default "0"} [:maybe :string]]])

;; Schema for Query/cards arguments
;; app.card/list handles its own defaults for limit and offset if they are not provided.
(registry/defschema ::cards-args
  [:map
   [:limit {:optional true} [:maybe :int]]
   [:offset {:optional true} [:maybe :int]]])

(def ^:private tagger (gql.compiler/merge-tag-with-type ::models/GameCard))

(def ^:private tag-and-transform (juxt #(gql.transformer/encode % ::models/Card) tagger))

(defresolver :Query/card
  "Retrieves a specific game card by its name and version."
  [:=> [:cat :any ::card-args :any]
   [:maybe ::models/GameCard]]
  [_context args _value]
  (some->> (get-by-name (:name args) (or (:version args) "0"))
           tag-and-transform
           (apply schema/tag-with-type)))

(defresolver :Query/cards
  "Retrieves a list of game cards with pagination."
  [:=> [:cat :any ::cards-args :any]
   [:vector ::models/Card]]
  [_context args _value]
  ;; card/list expects a map like {:limit l :offset o} and applies defaults if keys are missing.
  (->> (list args)
       (map tag-and-transform)
       (mapv (partial apply schema/tag-with-type))))
