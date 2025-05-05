(ns app.graphql.compiler
  "Compiles a malli schema to graphql"
  (:require
   [app.models]
   [camel-snake-kebab.core :as csk]
   [malli.core :as mc]))

(def ^:dynamic *type-collector* "atom that is bound to collect new types" nil)

(defn dispatch-mc-type
  [schema _ _ _]
  (mc/type schema))

(defn- new-object!
  [object-name compiled-object]
  (when-let [collector *type-collector*]
    (swap! collector assoc-in [:objects object-name] compiled-object))
  object-name)

(defn- ->graphql-type-name
  [schema]
  (case (mc/type schema)
    (:multi :map) (when-let [type-name (get (mc/properties schema) :graphql/type)]
                    (csk/->PascalCaseKeyword type-name))
    ::mc/schema (-> schema mc/form name  csk/->PascalCaseKeyword)))

(defn- ->graphql-field
  [[field-name _opts field-type]]
  [(csk/->camelCaseKeyword field-name) {:type field-type}])

(defmulti ->graphql-type dispatch-mc-type)

(defmethod ->graphql-type ::mc/schema
  [schema _ _ _]
  (let [graphql-name (->graphql-type-name schema)]
    (cond-> graphql-name
      (not (contains? @*type-collector*
                      graphql-name)) (new-object! (mc/walk (mc/deref-recursive schema) ->graphql-type)))))

(defmethod ->graphql-type :default
  [schema _ _ _]
  (mc/form schema))

(defmethod ->graphql-type :string
  [_ _ _ _]
  (list 'non-null 'String))

(defmethod ->graphql-type :int
  [_ _ _ _]
  (list 'non-null 'Int))

(defmethod ->graphql-type :enum
  [_ _ _ _]
  (list 'non-null 'String))

(defmethod ->graphql-type :time/instant
  [_ _ _ _]
  (list 'non-null 'Date))

(defmethod ->graphql-type :maybe
  [_ _ [type] _]
  type)

(defmethod ->graphql-type :vector
  [_ _ [[_ type]] _]
  (list 'list type))

(defmethod ->graphql-type :map
  [schema _ fields _]
  (let [fields (into {} (map ->graphql-field) fields)]
    (if-let [graphql-type (->graphql-type-name schema)]
      (new-object! graphql-type fields)
      fields)))

(defmethod ->graphql-type :cat
  [_ _ children _]
  (if (= 3 (count children))
    (second children)
    (throw (ex-info "field resolves must be 3-arity fns" {:arg-count (count children)}))))

(defmethod ->graphql-type :=>
  [_ _ [field-args return-type] _]
  (cond-> {:type return-type}
    field-args (assoc :fields field-args)))

(defn name->var->graphql-schema
  "Convert a map of graphql action name -> resolver or mutation var to a graphql schema"
  [map]
  (binding [*type-collector* (atom {})]
    (doseq [[k var] map]
      (swap! *type-collector* assoc-in [:objects
                                        (case (namespace k)
                                          "Query" :Query
                                          "Mutation" :Mutation)
                                        :fields
                                        (csk/->camelCaseKeyword (name k))]
             (mc/walk (-> var meta :schema) ->graphql-type)))
    @*type-collector*))
