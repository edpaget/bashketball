(ns app.card
  (:refer-clojure :exclude [list])
  (:require
   [app.db :as db]
   [app.models :as models]
   [malli.experimental :as me]
   [app.registry :as registry]))

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
                     :from   [:game_card]
                     :where  [:and [:= :name name]
                              [:= :version version]]})))

(registry/defschema ::pagination-opts
  [:map
   [:limit :int]
   [:offset :int]])

(me/defn list :- ::models/GameCard
  "Lists game cards with pagination using HoneySQL. Relies on dynamic db binding. "
  ([pagination-opts :- ::pagination-opts]
   (list [:*] pagination-opts))
  ([cols :- [:vector :keyword]
    {:keys [limit offset] :or {limit 100 offset 0}} :- ::pagination-opts]
   (db/execute! {:select    cols
                 :from     [:game_card]
                 :order-by [:name :version]
                 :limit    limit
                 :offset   offset})))
