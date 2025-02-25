(ns app.models.graphql-schema-adapter
  (:require [clojure.walk :as w]
            [clojure.core.match :as match]
            [camel-snake-kebab.core :as csk]
            [meta-merge.core :refer [meta-merge]]
            [malli.core :as mc]))

(declare malli-schema->graphql-schema)

(defn- ->graphql-field
  [[field-name _opts field-type]]
  [field-name field-type])

(defn- replace-dispatch
  [dispatch-field dispatch-enum]
  (fn
    [schema]
    (w/postwalk (fn [form]
                  (if (and  (vector? form)
                            (= dispatch-field (first form)))
                    {:type (list 'non-null dispatch-enum)}
                    form))
                schema)))

(def ->PascalKeyword (comp csk/->PascalCase keyword))

(defn- walk-malli-map->graphql-type
  [schema _ children _]
  (match/match [(mc/form schema) children]
    [:string _]  (list 'not-null 'String)
    [:int _] (list 'not-null 'Integer)
    [[:maybe _] [([_ type] :seq)]] type
    [[:vector _] [vector-type]] (cons 'list vector-type)
    [[:map (:or {:graphql/type graphql-type}
                {:type graphql-type}) & _] [& fields]]
    {:objects
     {(->PascalKeyword graphql-type)
      {:fields (->> (map ->graphql-field fields)
                    (into {}))}}}
    [[:multi {:dispatch dispatch-field} & _] [& sub-types]]
    (update-in
     (->> (map last sub-types)
          (map (replace-dispatch dispatch-field (->PascalKeyword dispatch-field)))
          (apply meta-merge))
     [:enums (->PascalKeyword dispatch-field)]
     update
     :values
     concat
     (->> (map last sub-types)
          (tree-seq coll? seq)
          (filter vector?)
          (filter #(= dispatch-field (first %)))
          (map (comp last last))
          (map (comp csk/->SCREAMING_SNAKE_CASE keyword))
          (into [])))
    [x _] x))

(defn malli-schema->graphql-schema
  "Convert a malli schema into graphql type(s)"
  [schema]
  (let [schema (mc/deref schema)]
    (mc/walk schema walk-malli-map->graphql-type)))

(comment
  (require '[app.models.core :as models.core])
  (require '[app.models.card])
  (require '[app.registry])
  (malli-schema->graphql-schema (models.core/schema :models/Card)))
