(ns app.graphql.compiler
  "Compiles a malli schema to graphql"
  (:require
   [app.models]
   [camel-snake-kebab.core :as csk]
   [malli.core :as mc]
   [clojure.string :as str]))

(def ^:dynamic *type-collector* "atom that is bound to collect new types" nil)

(defn- dispatch-mc-type
  [schema _ _ _]
  (mc/type schema))

(defn- new-object!
  [object-name compiled-object]
  (when-let [collector *type-collector*]
    (swap! collector assoc-in [:objects object-name :fields] compiled-object))
  object-name)

(defn- ->graphql-type-name
  [schema]
  (case (mc/type schema)
    (:multi :map) (when-let [type-name (get (mc/properties schema) :graphql/type)]
                    (csk/->PascalCaseKeyword type-name))
    ::mc/schema (-> schema mc/form name csk/->PascalCaseKeyword)))

(defn- ->graphql-field
  [[field-name _opts field-type]]
  [(csk/->camelCaseKeyword field-name) {:type field-type}])

(defmulti ^:Private ->graphql-type dispatch-mc-type)

(defmethod ->graphql-type ::mc/schema
  [schema _ _ _]
  (if (contains? #{:time/instant} (mc/form schema))
    (mc/walk (mc/deref schema) ->graphql-type)
    (let [graphql-name (->graphql-type-name schema)]
      (list 'non-null
            (cond-> graphql-name
              (not (contains? @*type-collector*
                              graphql-name)) (new-object! (-> (mc/deref-recursive schema)
                                                              (mc/walk ->graphql-type)
                                                              second)))))))

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
  [_ _ [[_ type]] _]
  type)

(defmethod ->graphql-type :vector
  [_ _ [type] _]
  (list 'list type))

(defmethod ->graphql-type :map
  [schema _ fields _]
  (let [fields (into {} (map ->graphql-field) fields)]
    (list 'non-null
          (if-let [graphql-type (->graphql-type-name schema)]
            (new-object! graphql-type fields)
            fields))))

(defmethod ->graphql-type :any
  [_ _ _ _]
  nil)

(defmethod ->graphql-type :cat
  [_ _ children _]
  (if (= 3 (count children))
    (second children)
    (throw (ex-info "field resolves must be 3-arity fns" {:arg-count (count children)}))))

(defmethod ->graphql-type :=>
  [_ _ [field-args return-type] _]
  (cond-> {:type return-type}
    field-args (assoc :fields (second field-args))))

(defn name->tuple->graphql-schema
  "Convert a map of graphql action name -> tuple of schema and fn of a graphql query or mutation
  to a graphql schema"
  [map]
  (binding [*type-collector* (atom {})]
    (doseq [[k tuple] map]
      (swap! *type-collector* assoc-in [:objects
                                        (case (namespace k)
                                          "Query" :Query
                                          "Mutation" :Mutation)
                                        :fields
                                        (csk/->camelCaseKeyword (name k))]
             (mc/walk (first tuple) ->graphql-type)))
    @*type-collector*))

(defmulti ^:private ->graphql-string
  "Compile a query form to a gql query"
  (fn [{:keys [type]} _ctx] type))

(defmethod ->graphql-string ::query
  [{:keys [children query-key]} ctx]
  (str (csk/->camelCase (name query-key))
       " { "
       (str/join " " (map ->graphql-string children (repeat (dissoc ctx ::in-field))))
       " }"))

(defmethod ->graphql-string ::fields
  [{:keys [schema children]} {:keys [app.graphql.compiler/in-field] :as ctx}]
  (str
   (when in-field
     (str "... on " (name (->graphql-type-name schema)) " { "))
   (str/join " " (map ->graphql-string children (repeat (assoc ctx ::in-field true))))
   (when in-field
     " }")))

(defmethod ->graphql-string ::field
  [{:keys [field]} _ctx]
  (name field))

(defmethod ->graphql-string ::root
  [{:keys [children]} ctx]
  (str "query { " (str/join " " (map ->graphql-string children (repeat ctx))) " }"))

(defmulti ^:private ->query-ast
  "Convert a query map to an ast while checking if it is valid and expanding schemas"
  (fn [form] (cond
               (seq? form)     ::query-with-args
               (vector? form)  ::fields
               (map? form)     ::query
               (keyword? form) ::field
               :else (throw (ex-info "Unsupported form" {:form form})))))

(defmethod ->query-ast ::query-with-args
  [[query & args]]
  {:type ::query-with-args
   :children [(->query-ast query)]
   :arguments args})

(defn- ->query-field-names
  [schema]
  (mc/walk (mc/deref schema)
           (fn [schema _ children _]
             (condp = (mc/type schema)
               :merge (->query-field-names schema)
               :map (into #{} (map first) children)
               :multi (into #{}
                            (comp
                             (map #(nth % 2))
                             (map #(vector %)))
                            children)
               schema))))

(defmethod ->query-ast ::fields
  [[schema & fields]]
  (when (nil? schema)
    (throw (ex-info "Form must have at least provide a schema" {})))
  {:type ::fields
   :children (if (empty? fields)
               (map ->query-ast (->query-field-names schema))
               (map ->query-ast fields))
   :schema schema})

(defmethod ->query-ast ::query
  [form]
  (map (fn [[k v]] {:type ::query
                    :children [(->query-ast v)]
                    :query-key k})
       form))

(defmethod ->query-ast ::field
  [form]
  {:type ::field
   :field (csk/->camelCaseKeyword (name form))})

(defn ->query
  "Take a map of graphql-query-name to a vector in the form [malli-schema field ...field-n] and turn it
  into a graphql query. If no fields are provided all fields will be selected. Nested queries can be
  added as a map of field-name->[malli-schema field ...field-n] specific types within an interface or
  union can be selected as a vector of vectors eg [malli-schema-interface interface-field [malli-schema type type field]].
  If a query takes arguments it's it can be wrapped in a seq with the query map as the first item and the
  arguments as pairs of [:name placeholder] in the tail.

  Returns a tuple of the query string and map of __typename->malli-schemas."
  [query]
  (let [ast {:type ::root :children (->query-ast query)}
        types (keep :schema (tree-seq #(or (seq? %) (map? %))
                                      #(or (:children %) (seq %))
                                      ast))]
    [(->graphql-string ast {})
     (zipmap (map (comp name ->graphql-type-name) types) types)]))

(comment
  (->query {:Query/me [:app.models/Actor :id :useName]})
  (->query {:Query/me [:app.models/GameCard]})
  (->query {:Query/me [:app.models/GameCard]})
  (->query {:Query/me [:app.models/Actor]}))
