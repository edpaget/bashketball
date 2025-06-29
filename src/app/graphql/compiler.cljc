(ns app.graphql.compiler
  "Compiles a malli schema to graphql"
  (:require
   [app.models]
   [camel-snake-kebab.core :as csk]
   [clojure.string :as str]
   [malli.core :as mc]))

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
    (:multi :map :enum) (when-let [type-name (or (get (mc/properties schema) :graphql/type)
                                                 (get (mc/properties schema) :graphql/interface))]
                          (csk/->PascalCaseKeyword type-name))
    ::mc/schema (-> schema mc/form name csk/->PascalCaseKeyword)))

(declare ->graphql-type)

(defn- trim-id
  "Remove the trailing `-id` from a keyword or string"
  [nameable]
  (str/replace (name nameable) #"-id$" ""))

(defn- ->graphql-field
  [[field-name opts field-type]]
  (when-not (or (:graphql/hidden opts) (nil? field-type))
    (if-let [fk (:app.models/fk opts)]
      [(-> (trim-id field-name)
           csk/->camelCaseKeyword) {:type (mc/walk (mc/schema fk) ->graphql-type)}]
      [(csk/->camelCaseKeyword field-name) {:type field-type}])))

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
  [_ _ _ _]
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
  [schema _ children _]
  (if-let [enum-gql-name (->graphql-type-name schema)]
    (do
      (new-object! enum-gql-name {:values (set (map name children))} :enums)
      (list 'non-null enum-gql-name))
    (list 'non-null 'String)))

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

(defn- pop-object!
  "Remove an object from the bound set of objects and return its fields"
  [object-name]
  (let [fields (get-in @*type-collector* [*object-type* object-name])]
    (swap! *type-collector* update *object-type* dissoc object-name)
    (:fields fields)))

(defn- compile-function
  "Extract the arguments from the malli scheme and convert it into a graphql schema"
  [type]
  (fn
    [schema _ children _]
    (case (mc/type schema)
      :=> (let [[field-args return-type] children]
            (cond-> {:type (mc/walk return-type ->graphql-type)}
              field-args (assoc :args field-args)))
      :cat  (binding [*object-type* (if (= type :Mutation) :input-objects :objects)]
              (let [[_ field-args] (if-not (= 3 (count children))
                                     (throw (ex-info "field resolvers must be 3-arity fns" {:arg-count (count children)}))
                                     (mc/walk (second children) ->graphql-type))]
                (cond-> field-args
                  (keyword? field-args) pop-object!)))
      schema)))

(defn name->tuple->graphql-schema
  "Convert a map of graphql action name -> tuple of schema and fn of a graphql query or mutation
  to a graphql schema"
  [map]
  (binding [*type-collector* (atom {})]
    (doseq [[k tuple] map]
      (when-let [compile-type (case (namespace k)
                                "Query" :Query
                                "Mutation" :Mutation
                                nil)]
        (swap! *type-collector* assoc-in [:objects
                                          compile-type
                                          :fields
                                          (csk/->camelCaseKeyword (name k))]
               (mc/walk (first tuple) (compile-function compile-type)))))
    @*type-collector*))

(defn- ->tag-map
  [schema _ children _]
  (case (mc/type schema)
    ::mc/schema (when-let [gql-type (mc/walk (mc/deref schema) ->tag-map)]
                  (if (= gql-type :map) (->graphql-type-name schema) gql-type))
    :multi (into {} (map (juxt first last)) children)
    :map (or (->graphql-type-name schema) :map)
    :merge (mc/walk (mc/deref schema) ->tag-map)
    nil))

(defn merge-tag-with-type
  "Takes a Malli schema (typically a :multi schema representing a GraphQL union or interface)
  and returns a function. This returned function, when given a data instance (model),
  uses the original schema's dispatch function to determine the model's concrete type
  and then returns the corresponding GraphQL type name (as a keyword).
  This is primarily used by Lacinia's :tag-with-type to resolve concrete types
  for unions and interfaces at query time."
  [schema]
  (let [derefed (-> schema mc/schema mc/deref)
        dispatch-fn (-> derefed mc/properties :dispatch)
        tag-map (mc/walk derefed ->tag-map)]
    (fn [model]
      (get tag-map (dispatch-fn model)))))

(defn- ->graphql-type-string
  [children]
  (letfn [(node->str [node]
            (condp = node
              'list "["
              ::end-list "]"
              'non-null ""
              ::end-null "!"
              (name node)))]
    (if-not (seq? children)
      (node->str children)
      (loop [cs (into [] children)
             accum ""]
        (if (empty? cs)
          accum
          (recur (cond-> (subvec cs 1)
                   (= 'non-null (first cs)) (conj ::end-null)
                   (= 'list (first cs)) (conj ::end-list))
                 (str accum (node->str (first cs)))))))))

(defn- ->graphql-argument-template
  [[query-key gql-type]]
  (str "$" (name query-key) ": " gql-type))

(defmulti ^:private ->graphql-string
  "Compile a query form to a gql query"
  (fn [{:keys [type]} _ctx] type))

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
   (when in-field
     (str "... on " (name (->graphql-type-name schema))))
   " { "
   (str/join " " (map ->graphql-string children (repeat (assoc ctx ::in-field true))))
   " }"))

(defmethod ->graphql-string ::field
  [{:keys [field]} _ctx]
  (name field))

(defmethod ->graphql-string ::root
  [{:keys [children name arguments operation-type]} ctx]
  (let [op-type (if (= operation-type :mutation) "mutation" "query")]
    (str op-type
         (when name
           (str " " name))
         (when arguments
           (str "(" (str/join ", " (map ->graphql-argument-template arguments)) ")"))
         " { "
         (str/join " " (map #(->graphql-string % ctx) children))
         " }")))

(defmulti ^:private ->query-ast
  "Convert a query map to an ast while checking if it is valid and expanding schemas"
  (fn [form] (cond
               (seq? form) ::with-args
               (vector? form) ::fields
               (map? form) ::query
               (keyword? form) ::field
               :else (throw (ex-info "Unsupported form" {:form form})))))

(defmethod ->query-ast ::with-args
  [[query & args]]
  {:type ::with-args
   :children [(->query-ast query)]
   :arguments args})

(defn- ->query-field-names
  [schema]
  (mc/walk (mc/deref schema)
           (fn [schema _ children _]
             (condp = (mc/type schema)
               :merge (->query-field-names schema)
               :map (into #{} (comp (remove #(some #{:graphql/hidden :app.models/fk} (keys (second %))))
                                    (map first)) children)
               :multi (into #{}
                            (comp
                             (map #(nth % 2))
                             (map #(vector %)))
                            children)
               schema))))

(def field-sentinel ::all-fields)

(defmethod ->query-ast ::fields
  [[schema & fields]]
  (when (nil? schema)
    (throw (ex-info "Form must have at least provide a schema" {})))
  {:type ::fields
   :children (cond
               (empty? fields) (map ->query-ast (->query-field-names schema))
               (= field-sentinel (first fields)) (concat (map ->query-ast (->query-field-names schema))
                                                         (map ->query-ast (rest fields)))
               :else (map ->query-ast fields))
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
   :form form
   :field (csk/->camelCaseKeyword (name form))})

(defn- compile-operation-arguments
  [arguments]
  (when arguments
    (let [schema (mc/schema arguments)]
      (case (mc/type schema)
        ::mc/schema
        (compile-operation-arguments (mc/deref schema))
        :map
        (for [[var-name _ var-type] (mc/children schema)]
          [(csk/->camelCaseString (name var-name))
           (->graphql-type-string (mc/walk var-type ->graphql-type))])))))

(defn- build-type-map
  [ast]
  (let [types (keep :schema (tree-seq #(or (seq? %) (map? %))
                                    #(or (:children %) (seq %))
                                    ast))]
    (zipmap (map (comp name ->graphql-type-name) types) types)))

(defn ->query
  "Constructs a GraphQL query string and a map of __typename to Malli schemas.

  The function can be called with one, two, or three arguments:
  - `(->query query-desc)`
  - `(->query query-desc operation-name)`
  - `(->query query-desc operation-name operation-args)`

  1. `query-desc` (Map):
     Describes the GraphQL selection set.
     - Keys: GraphQL query/mutation names (e.g., :Query/me, :Mutation/createUser).
     - Values: Vectors defining the selection for that query/mutation.

     Selection Vector Format: `[malli-schema field1 field2 ...]`
     - `malli-schema`: Malli schema keyword (e.g., :app.models/User).
     - `field1, field2, ...`: Keywords for fields to select.
     - If no fields are listed after the schema, all applicable fields from the
       schema are selected (those not marked :graphql/hidden or :app.models/fk).

     Nested Selections:
     Use a map for a field's value to specify a nested selection:
     `{:field-name [nested-malli-schema :nested-field1 ...]}`

     Interface/Union Specific Fields (Inline Fragments):
     Use a nested vector to select fields for a concrete type within an interface/union:
     `[interface-schema :common-field [concrete-type-schema :specific-field1 ...]]`

     Field Arguments:
     To pass arguments to a field, wrap the selection vector and its arguments in a sequence:
     `{:Query/user '([:app.models/User :id]} :userId)`
     This generates a field call like `user(userId: $userId) { id }`. The GraphQL
     variable `$userId` must then be defined in `operation-args`.

  2. `operation-name` (String, optional):
     An optional name for the GraphQL operation (e.g., \"GetUserQuery\").

  3. `operation-args` (Malli `:map` schema, optional):
     Defines variables for the GraphQL operation from a Malli `:map` schema.
     For example, `[:map [:userId :uuid]]` defines a variable `$userId` of type `Uuid!`.
     A non-maybe type implies a non-null GraphQL type.

  Returns:
  A tuple: `[query-string, typename->schema-map]`
  - `query-string`: The generated GraphQL query string.
  - `typename->schema-map`: A map from GraphQL __typename (string) to its Malli schema.

  Examples:

  ;; Basic query for specific fields
  (->query {:Query/me [:app.models/Actor :id :userName]})
  ; => [\"query { me { id userName } }\", {\"Actor\" :app.models/Actor}]

  ;; Query for all fields of a type (fields derived from schema)
  (->query {:Query/gameCard [:app.models/GameCard]})
  ; => [\"query { gameCard { <all_fields_from_GameCard_schema> } }\",
  ;     {\"GameCard\" :app.models/GameCard}]

  ;; Query with an operation name and operation arguments
  (->query {:Query/userById [:app.models/User :id :email]}
           \"GetUser\"
           [:map [:userId :uuid]]) ; :uuid implies Uuid!
  ; => [\"query GetUser($userId: Uuid!) { userById { id email } }\",
  ;     {\"User\" :app.models/User}]

  ;; Query with field arguments (linking to operation arguments)
  (->query '({:Query/userSearch [:app.models/User :name]} :searchTerm) ; Field arg
           \"SearchUsers\"
           [:map [:searchTerm [:maybe :string]]]) ; Operation arg, [:maybe :string] implies String
  ; => [\"query SearchUsers($searchTerm: String) { userSearch(searchTerm: $searchTerm) { name } }\",
  ;     {\"User\" :app.models/User}]

  ;; Nested query
  (->query {:Query/viewer [:app.models/Viewer
                           :id
                           {:profile [:app.models/Profile :firstName :lastName]}]})
  ; => [\"query { viewer { id profile { firstName lastName } } }\",
  ;     {\"Viewer\" :app.models/Viewer, \"Profile\" :app.models/Profile}]

  ;; Querying fields from specific types in an interface/union (inline fragments)
  (->query {:Query/node [:app.models/NodeInterface
                         :id
                         [:app.models/UserNode :email]    ; ... on UserNode
                         [:app.models/PostNode :title]]}) ; ... on PostNode
  ; => [\"query { node { id ... on UserNode { email } ... on PostNode { title } } }\",
  ;     {\"NodeInterface\" :app.models/NodeInterface,
  ;      \"UserNode\" :app.models/UserNode,
  ;      \"PostNode\" :app.models/PostNode}]
  "
  ([query]
   (->query query nil nil))
  ([query operation-name] (->query query operation-name nil))
  ([query operation-name arguments]
   (let [ast {:type ::root
              :children (->query-ast query)
              :name operation-name
              :arguments (compile-operation-arguments arguments)}]
     [(->graphql-string ast {})
      (build-type-map ast)])))

(defn ->mutation
  "Constructs a GraphQL mutation string and a map of __typename to Malli schemas.

  This function is analogous to `->query`, but generates a `mutation` operation.
  It accepts the same arguments and follows the same structure for describing
  the selection set on the mutation's return payload.

  The function can be called with one, two, or three arguments:
  - `(->mutation mutation-desc)`
  - `(->mutation mutation-desc operation-name)`
  - `(->mutation mutation-desc operation-name operation-args)`

  1. `mutation-desc` (Map):
     Describes the GraphQL selection set for the mutation.
     - Keys: GraphQL mutation names (e.g., :Mutation/createUser).
     - Values: Vectors defining the selection for that mutation's return payload.
       The format is identical to `->query`'s `query-desc`.

     Selection Vector Format: `[malli-schema :field1 :field2 ...]`

  2. `operation-name` (String, optional):
     An optional name for the GraphQL mutation operation (e.g., \"CreateUserMutation\").

  3. `operation-args` (Malli `:map` schema, optional):
     Defines variables for the GraphQL mutation operation from a Malli `:map` schema.
     The format is identical to `->query`'s `operation-args`.

  Returns:
  A tuple: `[mutation-string, typename->schema-map]`
  - `mutation-string`: The generated GraphQL mutation string.
  - `typename->schema-map`: A map from GraphQL __typename (string) to its Malli schema.

  Examples:

  ;; Basic mutation for specific fields in the return payload
  (->mutation {:Mutation/createGame [:app.models/Game :id :status]})
  ; => [\"mutation { createGame { id status } }\", {\"Game\" :app.models/Game}]

  ;; Mutation with an operation name and arguments, returning a selection set.
  (->mutation '{:Mutation/createPost ([:app.models/Post :id :title] :title :content)}
              \"CreatePost\"
              [:map [:title :string] [:content :string]])
  ; => [\"mutation CreatePost($title: String!, $content: String!) { createPost(title: $title, content: $content) { id title } }\",
  ;     {\"Post\" :app.models/Post}]
  "
  ([query]
   (->mutation query nil nil))
  ([query operation-name] (->mutation query operation-name nil))
  ([query operation-name arguments]
   (let [ast {:type ::root
              :operation-type :mutation
              :children (->query-ast query)
              :name operation-name
              :arguments (compile-operation-arguments arguments)}]
     [(->graphql-string ast {})
      (build-type-map ast)])))

(comment
  (->query {:Query/me [:app.models/Actor :id :useName]})
  (->query {:Query/me [:app.models/GameCard]})
  (->query {:Query/me [:app.models/GameCard]})
  (->query {:Query/me [:app.models/Actor]}))
