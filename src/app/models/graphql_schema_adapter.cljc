(ns app.models.graphql-schema-adapter
  (:require [clojure.walk :as w]
            [camel-snake-kebab.core :as csk]
            [meta-merge.core :refer [meta-merge]]
            [malli.core :as mc]))

(defn malli-schema->graphql-type-name
  [schema]
  (let [schema (mc/deref schema)]
    (csk/->PascalCaseKeyword
     (or (get (mc/properties schema) :graphql/type)
         (get (mc/properties schema) :graphql/union-type)
         (get (mc/properties schema) :type)))))

(defn- ->graphql-field
  [[field-name _opts field-type]]
  [(csk/->camelCaseKeyword field-name) {:type field-type}])

(defn- replace-dispatch
  [dispatch-field dispatch-enum]
  (fn
    [schema]
    (w/postwalk (fn [form]
                  (if (and  (vector? form)
                            (= dispatch-field (first form)))
                    {dispatch-field {:type (list 'non-null dispatch-enum)}}
                    form))
                schema)))

(defmulti walk-malli-map->graphql-type (fn [schema _ _ _] (mc/type schema)))

(defmulti walk-malli-map->graphql-args (fn [schema _ _ _] (mc/type schema)))

(defmethod walk-malli-map->graphql-type :default
  [schema _ _ _]
  (mc/form schema))

(defmethod walk-malli-map->graphql-type :string
  [_ _ _ _]
  (list 'non-null 'String))

(defmethod walk-malli-map->graphql-type :int
  [_ _ _ _]
  (list 'non-null 'Int))

(defmethod walk-malli-map->graphql-type :enum
  [_ _ _ _]
  (list 'non-null 'String))

(defmethod walk-malli-map->graphql-type :time/zoned-date-time
  [_ _ _ _]
  (list 'non-null 'Date))

(defmethod walk-malli-map->graphql-type :maybe
  [_ _ [[_ type]] _]
  type)

(defmethod walk-malli-map->graphql-type :vector
  [_ _ [[_ type]] _]
  (list 'list type))

(defmethod walk-malli-map->graphql-type :map
  [schema _ fields _]
  (when-let [graphql-type (malli-schema->graphql-type-name schema)]
    {:objects
     {graphql-type
      {:fields (->> (map ->graphql-field fields)
                    (into {}))}}}))

(defmethod walk-malli-map->graphql-type :multi
  [schema _ sub-types _]
  (let [union-type (get (mc/properties schema) :graphql/union-type)
        dispatch-field (-> (mc/properties schema)
                           (get :dispatch)
                           csk/->camelCaseKeyword)]
    (cond-> (->> (map last sub-types)
                 (map (replace-dispatch dispatch-field (csk/->PascalCaseKeyword dispatch-field)))
                 (apply meta-merge))
      dispatch-field (update-in
                      [:enums (csk/->PascalCaseKeyword dispatch-field)]
                      update
                      :values
                      concat
                      (->> (map last sub-types)
                           (tree-seq coll? seq)
                           (filter vector?)
                           (filter #(and (= dispatch-field (first %))
                                         (map? (second %))
                                         (= := ((comp first first vals second) %))))
                           (map (comp last first vals last))
                           (map csk/->SCREAMING_SNAKE_CASE_KEYWORD)
                           (into [])))
      union-type (assoc-in
                  [:unions (csk/->PascalCaseKeyword union-type) :members]
                  (->> (map last sub-types)
                       (map (comp keys :objects))
                       flatten
                       (into []))))))

(defmethod walk-malli-map->graphql-args :default
  [& args]
  (apply walk-malli-map->graphql-type args))

(defmethod walk-malli-map->graphql-args :map
  [_ _ fields _]
  {:args
   (->> (map ->graphql-field fields)
        (into {}))})

(defn malli-schema->graphql-schema
  "Convert a malli schema into graphql type(s)"
  [schema]
  (mc/walk schema walk-malli-map->graphql-type))

(defn malli-schema->graph-args
  [schema]
  (mc/walk schema walk-malli-map->graphql-args))

(comment
  (require '[app.models.core])
  (require '[app.models.card])
  (require '[app.registry])
  (malli-schema->graphql-schema (app.models.core/schema :models/Card)))
