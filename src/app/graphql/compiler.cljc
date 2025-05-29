(ns app.graphql.compiler
  "Compiles a malli schema to graphql"
  (:require
   [app.models]
   [camel-snake-kebab.core :as csk]
   [malli.core :as mc]
   [clojure.string :as str]))

(def ^:dynamic *type-collector* "atom that is bound to collect new types" nil)
(def ^:dynamic *object-type* "Switches what type of object is " :objects)

(defn- dispatch-mc-type
  [schema _ _ _]
  (mc/type schema))

(defn- new-object!
  ([object-name compiled-object]
   (new-object! object-name compiled-object *object-type*))
  ([object-name compiled-object type]
   (when-let [collector *type-collector*]
     (swap! collector assoc-in [type object-name] compiled-object))
   object-name))

(defn- ->graphql-type-name
  [schema]
  (case (mc/type schema)
    (:multi :map) (when-let [type-name (or (get (mc/properties schema) :graphql/type)
                                           (get (mc/properties schema) :graphql/interface))]
                    (csk/->PascalCaseKeyword type-name))
    ::mc/schema (-> schema mc/form name csk/->PascalCaseKeyword)))

(defn- ->graphql-field
  [[field-name opts field-type]]
  (when-not (or (:graphql/hidden opts) (nil? field-type))
    [(csk/->camelCaseKeyword field-name) {:type field-type}]))

(defmulti ^:Private ->graphql-type dispatch-mc-type)

(defmethod ->graphql-type ::mc/schema
  [schema _ _ _]
  (let [type-from-schema (mc/walk (mc/deref schema) ->graphql-type)]
    (if-not (map? (second type-from-schema))
      type-from-schema
      (list 'non-null (new-object! (->graphql-type-name schema)
                                   {:fields (second type-from-schema)})))))

(defmethod ->graphql-type :default
  [schema _ _ _]
  (mc/form schema))

(defmethod ->graphql-type :=
  [schema _ _ _]
  nil)

(defmethod ->graphql-type :uuid
  [_ _ _ _]
  (list 'non-null 'Uuid))

(defmethod ->graphql-type :string
  [_ _ _ _]
  (list 'non-null 'String))

(defmethod ->graphql-type :int
  [_ _ _ _]
  (list 'non-null 'Int))

(defmethod ->graphql-type :boolean
  [_ _ _ _]
  (list 'non-null 'Boolean))

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
  (let [fields (into {} (comp (map ->graphql-field)
                              (remove nil?))
                     fields)
        graphql-type (->graphql-type-name schema)
        implements (->> (-> schema mc/properties :graphql/implements)
                        (map mc/schema)
                        (map #(mc/walk % ->graphql-type))
                        (mapv second))]
    (list 'non-null
          (cond
            (-> schema mc/properties :graphql/type)
            (new-object! graphql-type (cond-> {:fields fields}
                                        (seq implements) (assoc :implements implements)))
            (-> schema mc/properties :graphql/interface)
            (new-object! graphql-type {:fields fields} :interfaces)

            :else
            fields))))

(defmethod ->graphql-type :multi
  [schema _ children _]
  (list 'non-null
        (new-object! (->graphql-type-name schema)
                     {:members (->> (map #(nth % 2) children)
                                    (mapv second))}
                     :unions)))

(defmethod ->graphql-type :merge
  [schema _ _ _]
  (mc/walk (mc/deref schema) ->graphql-type))

(defmethod ->graphql-type :any
  [_ _ _ _]
  nil)

(defn- compile-function
  "Extract the arguments from the malli scheme and convert it into a graphql schema"
  [type]
  (fn
    [schema _ children _]
    (case (mc/type schema)
      :=> (let [[field-args return-type] children]
            (cond-> {:type (mc/walk return-type ->graphql-type)}
              field-args (assoc :fields (second field-args))))
      :cat (cond
             (not= 3 (count children)) (throw (ex-info "field resolvers must be 3-arity fns" {:arg-count (count children)}))
             (= type :Mutation) (binding [*object-type* :input-objects]
                                  (mc/walk (second children) ->graphql-type))
             :else (mc/walk (second children) ->graphql-type))
      schema)))

(defn name->tuple->graphql-schema
  "Convert a map of graphql action name -> tuple of schema and fn of a graphql query or mutation
  to a graphql schema"
  [map]
  (binding [*type-collector* (atom {})]
    (doseq [[k tuple] map]
      (let [compile-type  (case (namespace k)
                            "Query" :Query
                            "Mutation" :Mutation)]
        (swap! *type-collector* assoc-in [:objects
                                          compile-type
                                          :fields
                                          (csk/->camelCaseKeyword (name k))]
               (mc/walk (first tuple) (compile-function compile-type)))))
    @*type-collector*))

(defn ->graphql-type-string
  [children]
  (letfn [(node->str [node]
            (condp = node
              'list  "["
              ::end-list "]"
              'non-null "!"
              (name node)))]
    (loop [[child & next] (into [] children)
           accum ""]
      (if (empty? next)
        (str accum (node->str child))
        (recur (cond-> next
                 (= 'list child) (conj next ::end-list))
               (str accum (node->str child)))))))

(defn ->graphql-argument-template
  [[query-key gql-type]]
  (str "$" (name query-key) ": " gql-type))

(defmulti ^:private ->graphql-string
  "Compile a query form to a gql query"
  (fn [{:keys [type]} _ctx]  type))

(defmethod ->graphql-string ::with-args
  [{:keys [children arguments]} ctx]
  (str "("
       (str/join ", " (map #(str (csk/->camelCaseString %)
                                 ": $"
                                 (csk/->camelCaseString %))
                           arguments))
       ")"
       (->graphql-string (first children) ctx)))

(defmethod ->graphql-string ::query
  [{:keys [children query-key]} ctx]
  (str (csk/->camelCase (name query-key))
       (str/join " " (map ->graphql-string children (repeat (dissoc ctx ::in-field))))))

(defmethod ->graphql-string ::fields
  [{:keys [schema children]} {:keys [app.graphql.compiler/in-field] :as ctx}]
  (str
   " { "
   (when in-field
     (str "... on " (name (->graphql-type-name schema)) " { "))
   (str/join " " (map ->graphql-string children (repeat (assoc ctx ::in-field true))))
   (when in-field
     " }")
   " }"))

(defmethod ->graphql-string ::field
  [{:keys [field]} _ctx]
  (name field))

(defmethod ->graphql-string ::root
  [{:keys [children name arguments]} ctx]
  (str "query"
       (when name
         (str " " name))
       (when arguments
         (str "(" (str/join ", " (map ->graphql-argument-template arguments)) ")"))
       " { "
       (str/join " " (map ->graphql-string children (repeat ctx))) " }"))

(defmulti ^:private ->query-ast
  "Convert a query map to an ast while checking if it is valid and expanding schemas"
  (fn [form] (cond
               (seq? form)     ::with-args
               (vector? form)  ::fields
               (map? form)     ::query
               (keyword? form) ::field
               :else (throw (ex-info "Unsupported form" {:form form})))))

(defmethod ->query-ast ::with-args
  [[query & args]]
  {:type      ::with-args
   :children  [(->query-ast query)]
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
  {:type     ::fields
   :children (if (empty? fields)
               (map ->query-ast (->query-field-names schema))
               (map ->query-ast fields))
   :schema   schema})

(defmethod ->query-ast ::query
  [form]
  (map (fn [[k v]] {:type      ::query
                    :children  [(->query-ast v)]
                    :query-key k})
       form))

(defmethod ->query-ast ::field
  [form]
  {:type  ::field
   :form  form
   :field (csk/->camelCaseKeyword (name form))})

(defn ->query
  "Take a map of graphql-query-name to a vector in the form [malli-schema field ...field-n] and turn it
  into a graphql query. If no fields are provided all fields will be selected. Nested queries can be
  added as a map of field-name->[malli-schema field ...field-n] specific types within an interface or
  union can be selected as a vector of vectors eg [malli-schema-interface interface-field [malli-schema type type field]].
  If a query takes arguments it's it can be wrapped in a seq with the query map as the first item and the
  arguments as pairs of [:name placeholder] in the tail.

  Returns a tuple of the query string and map of __typename->malli-schemas."
  ([query]
   (->query query nil nil))
  ([query operation-name] (->query query operation-name nil))
  ([query operation-name arguments]
   (let [ast {:type ::root
              :children (->query-ast query)
              :name operation-name
              :arguments (when arguments
                           (map (fn [[gql-var gql-type]]
                                  [(csk/->camelCaseString (name gql-var))
                                   (->graphql-type-string (mc/walk gql-type ->graphql-type))])
                                arguments))}

         types (keep :schema (tree-seq #(or (seq? %) (map? %))
                                       #(or (:children %) (seq %))
                                       ast))]
     [(->graphql-string ast {})
      (zipmap (map (comp name ->graphql-type-name) types) types)])))

(comment
  (->query {:Query/me [:app.models/Actor :id :useName]})
  (->query {:Query/me [:app.models/GameCard]})
  (->query {:Query/me [:app.models/GameCard]})
  (->query {:Query/me [:app.models/Actor]}))
