(ns app.graphql.compiler-test
  (:require
   #?(:clj [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test :refer [deftest is testing] :include-macros true])
   [app.graphql.compiler :as sut]
   [clojure.string :as str]
   [app.registry :as registry]
   [malli.core :as mc]))

;; --- Test Data ---

(def SimpleObject
  [:map {:graphql/type "SimpleObject"}
   [:id :int]
   [:name :string]])

(def ComplexObject
  [:map {:graphql/type "ComplexObject"}
   [:id :int]
   [:simple [:maybe SimpleObject]]
   [:simples [:vector SimpleObject]]
   [:created-at :time/instant]])

(def Actor
  [:map {:graphql/type "Actor"}
   [:id :int]
   [:user-name :string] ; Kebab case field name
   [:email :string]])

(def Team
  [:map {:graphql/type "Team"}
   [:id :int]
   [:name :string]
   [:players [:vector Actor]]])

(def Game
  [:map {:graphql/type "Game"}
   [:id :int]
   [:title :string]
   [:home-team Team]
   [:away-team [:maybe Team]]])

(def SchemaWithoutGraphQLType
  [:map
   [:id :int]])

;; Schemas for mutation input type tests
(def InputPayloadDirect [:map {:graphql/type "InputPayloadDirect"} [:value :string]])

(def RegisteredInputPayloadForTest [:map {:graphql/type "RegisteredInputForTest"} [:count :int]])

(def MutationOutputForTest [:map {:graphql/type "MutationOutputForTest"} [:message :string]])

;; Schema for anonymous map argument tests
(def PlainMapArgSchema [:map [:filter :string] [:limit :int]])

;; Schemas for ->query complex argument tests
(def ComplexInputTypeTest [:map {:graphql/type "ComplexInputTypeTest"} [:filter :string] [:count :int]])
(def ComplexInputQueryResult [:map {:graphql/type "ComplexInputQueryResult"} [:status :boolean]])

;; --- Local Schemas for Inline Fragment Test ---

(def TestInterfaceBase
  [:map {:graphql/interface "TestInterfaceBase"}
   [:commonField :string]])

(def TestConcreteTypeA
  [:merge TestInterfaceBase
   [:map {:graphql/type "TestConcreteTypeA"}
    [:fieldA :int]]])

(def TestConcreteTypeB
  [:merge TestInterfaceBase
   [:map {:graphql/type "TestConcreteTypeB"}
    [:fieldB :boolean]]])

;; Define a multi-schema to act as the interface/union
(def TestInterfaceMulti
  [:multi {:dispatch (fn [m] (cond (contains? m :fieldA) ::TestConcreteTypeA
                                   (contains? m :fieldB) ::TestConcreteTypeB
                                   :else ::TestInterfaceBase)) ; Dispatch logic example
           :graphql/type "TestInterfaceMulti"} ; Explicit GraphQL type name for the interface/union
   [::TestConcreteTypeA TestConcreteTypeA]
   [::TestConcreteTypeB TestConcreteTypeB]])

;; Define vars with schemas in metadata for testing
(def get-simple-object-var [[:=> [:cat :any [:map [:id :int]] :any] [:maybe SimpleObject]]
                            nil])

(def get-complex-object-var [[:=> [:cat :any :any :any] [:maybe ComplexObject]]
                             nil])

(def create-simple-object-var [[:=> [:cat :any [:map [:name :string]] :any] :string]
                               nil])

(def get-list-of-strings-var [[:=> [:cat :any :any :any] [:vector :string]]
                              nil])

(def get-optional-int-var [[:=> [:cat :any :any :any] [:maybe :int]]
                           nil])

(def get-items-by-plain-map-filter-var
  [[:=> [:cat :any [:map [:criteria PlainMapArgSchema]] :any] [:vector :string]] nil])

(def update-items-by-plain-map-filter-var
  [[:=> [:cat :any [:map [:config PlainMapArgSchema]] :any] :boolean] nil])

;; Schemas for ->graphql-type :map method tests
(def MapTestInterfaceSchema
  [:map {:graphql/interface "MapTestInterface"}
   [:id :uuid]
   [:interface-field :string]])

(def MapTestInterfaceOne
  [:map {:graphql/interface "MapTestInterfaceOne"}
   [:common-field-a :int]])

(def MapTestInterfaceTwo
  [:map {:graphql/interface "MapTestInterfaceTwo"}
   [:common-field-b :boolean]])

(def MapTestImplementingType
  [:merge
   MapTestInterfaceOne
   MapTestInterfaceTwo
   [:map {:graphql/type "MapTestImplementingType"
          :graphql/implements [MapTestInterfaceOne MapTestInterfaceTwo]}
    [:id :uuid] ; Assuming id is part of MyImplementingType, not necessarily from an interface here
    [:unique-type-field :string]]])

(def MapTestRegisteredInterfaceSchema
  [:map {:graphql/interface "MapTestRegisteredInterface"}
   [:registry-common-field :string]])

(def MapTestImplementingTypeViaRegistered
  [:merge ::MapTestRegisteredInterfaceSchema
   [:map {:graphql/type "MapTestImplementingTypeViaRegistered"
          :graphql/implements [::MapTestRegisteredInterfaceSchema]} ; Using a keyword to refer to registry
    [:id :uuid]]])

(def MapTestPlainMapSchema
  [:map
   [:plain-field-x :string]
   [:plain-field-y :boolean]])

;; Schemas for ->graphql-type :multi method test
(def UnionMemberOneSchema
  [:map {:graphql/type "UnionMemberOne"}
   [:field-alpha :string]])

(def UnionMemberTwoSchema
  [:map {:graphql/type "UnionMemberTwo"}
   [:field-beta :boolean]])

(def TestUnionViaMultiSchema
  [:multi {:dispatch (fn [m] (cond (:field-alpha m) ::UnionMemberOneSchema ; Using keywords for registry lookup
                                   (:field-beta m) ::UnionMemberTwoSchema))
           :graphql/type "TestUnionViaMulti"} ; Name for the GraphQL Union
   [::UnionMemberOneSchema UnionMemberOneSchema]
   [::UnionMemberTwoSchema UnionMemberTwoSchema]])

;; Schema for :graphql/hidden test
(def MapWithHiddenFieldSchema
  [:map {:graphql/type "MapWithHiddenField"}
   [:visible-field :string]
   [:hidden-field {:graphql/hidden true} :int]
   [:another-visible-field :boolean]])

;; Schema for := exclusion test
(def MapWithEqualsFieldSchema
  [:map {:graphql/type "MapWithEqualsField"}
   [:normal-field :string]
   [:equals-field [:= "constant-value"]] ; This field should be excluded
   [:another-normal-field :int]])

;; --- Tests ---

(deftest name->tuple->graphql-schema-test
  (testing "empty input map"
    (is (= {} (sut/name->tuple->graphql-schema {}))
        "Should return an empty map for empty input"))

  (testing "single query field with simple return type (vector of strings)"
    (let [result (sut/name->tuple->graphql-schema {:Query/getListOfStrings get-list-of-strings-var})]
      (is (= {:objects
              {:Query {:fields {:getListOfStrings {:type '(list (non-null String))}}}}}
             result)
          "Should correctly compile a query returning a list of non-null strings")))

  (testing "single query field with optional scalar return type"
    (let [result (sut/name->tuple->graphql-schema {:Query/getOptionalInt get-optional-int-var})]
      (is (= {:objects
              {:Query {:fields {:getOptionalInt {:type 'Int}}}}} ; :maybe :int becomes Int
             result)
          "Should correctly compile a query returning an optional Int")))

  (testing "single query field with object return type and arguments"
    (let [result (sut/name->tuple->graphql-schema {:Query/getSimpleObject get-simple-object-var})]
      (is (= {:objects
              {:Query {:fields {:getSimpleObject {:type :SimpleObject ; Return type name
                                                  :args {:id {:type '(non-null Int)}}}}} ; Argument type
               ;; The SimpleObject type definition should also be collected
               :SimpleObject {:fields {:id {:type '(non-null Int)}
                                       :name {:type '(non-null String)}}}}}
             result)
          "Should compile query with args, object return type, and collect the object type")))

  (testing "single mutation field with arguments and simple return type"
    (let [result (sut/name->tuple->graphql-schema {:Mutation/createSimpleObject create-simple-object-var})]
      (is (= {:objects
              {:Mutation {:fields {:createSimpleObject {:type '(non-null String) ; Return type
                                                        :args {:name {:type '(non-null String)}}}}}}} ; Argument type
             result)
          "Should correctly compile a mutation with args and a simple return type")))

  (testing "query with complex nested object return type"
    (let [result (sut/name->tuple->graphql-schema {:Query/getComplexObject get-complex-object-var})]
      (is (= {:objects
              {:Query {:fields {:getComplexObject {:type :ComplexObject}}} ; Return type name
               ;; Both ComplexObject and its nested SimpleObject should be collected
               :SimpleObject {:fields {:id {:type '(non-null Int)}
                                       :name {:type '(non-null String)}}}
               :ComplexObject {:fields {:id {:type '(non-null Int)}
                                        :simple {:type :SimpleObject} ; Optional field type
                                        :simples {:type '(list (non-null :SimpleObject))} ; List of objects
                                        :createdAt {:type '(non-null Date)}}}}} ; :time/instant -> Date
             result)
          "Should compile query with complex object return type and collect all nested types"))

    (testing "multiple query and mutation fields"
      (let [result (sut/name->tuple->graphql-schema
                    {:Query/getSimpleObject get-simple-object-var
                     :Query/getListOfStrings get-list-of-strings-var
                     :Mutation/createSimpleObject create-simple-object-var})]
        (is (= {:objects
                {:Query {:fields {:getSimpleObject {:type :SimpleObject
                                                    :args {:id {:type '(non-null Int)}}}
                                  :getListOfStrings {:type '(list (non-null String))}}}
                 :Mutation {:fields {:createSimpleObject {:type '(non-null String)
                                                          :args {:name {:type '(non-null String)}}}}}
               ;; SimpleObject type collected once
                 :SimpleObject {:fields {:id {:type '(non-null Int)}
                                         :name {:type '(non-null String)}}}}}
               result)
            "Should correctly compile multiple queries and mutations, collecting types"))))

  (testing "mutation with various named input types and an output type"
    (let [type-registry-value @registry/type-registry] ; Save current registry state
      (try
        ;; Register one of the input types to test ::mc/schema pathway for inputs
        (registry/register-type! ::RegisteredInputPayloadForTest RegisteredInputPayloadForTest)

        (let [;; Define the mutation var using the schemas
              process-data-mutation-var [[:=> [:cat :any ; context map (e.g., from ring)
                                               [:map ; arguments map for the mutation
                                                [:directPayload InputPayloadDirect]
                                                [:registeredPayload ::RegisteredInputPayloadForTest]]
                                               :any] ; resolver context (e.g., from lacinia)
                                          MutationOutputForTest] ; Return type schema
               ;; Dummy resolver function
                                         (fn [_args _ctx] {:message "processed"})]

              schema-map {:Mutation/processComplexData process-data-mutation-var}
              compiled-schema (sut/name->tuple->graphql-schema schema-map)]

          (is (= {:objects
                  {;; Mutation definition under :objects
                   :Mutation
                   {:fields
                    {:processComplexData {:type '(non-null :MutationOutputForTest)
                                          :args {:directPayload {:type '(non-null :InputPayloadDirect)}
                                                 :registeredPayload {:type '(non-null :RegisteredInputForTest)}}}}}
                   ;; Output type definition under :objects
                   :MutationOutputForTest {:fields {:message {:type '(non-null String)}}}}

                  ;; Input types definitions under :input-objects
                  :input-objects
                  {;; Input type from a direct map schema (should be correctly compiled)
                   :InputPayloadDirect {:fields {:value {:type '(non-null String)}}}
                   :RegisteredInputForTest {:fields {:count {:type '(non-null Int)}}}}}
                 compiled-schema)
              "Should compile mutation with named input types, collecting output type correctly, direct input type correctly, and registered input type reflecting current compiler behavior for ::mc/schema."))
        (finally
          (reset! registry/type-registry type-registry-value)))))

  (testing "query with an anonymous map schema as argument type"
    (let [result (sut/name->tuple->graphql-schema {:Query/getItemsByPlainMapFilter get-items-by-plain-map-filter-var})]
      (is (= {:objects
              {:Query {:fields {:getItemsByPlainMapFilter
                                {:type '(list (non-null String))
                                 :args {:criteria {:type '(non-null {:filter {:type (non-null String)}
                                                                     :limit {:type (non-null Int)}})}}}}}}}
             result)
          "Should compile query with an anonymous map argument, inlining its fields.")))

  (testing "mutation with an anonymous map schema as argument type"
    (let [result (sut/name->tuple->graphql-schema {:Mutation/updateItemsByPlainMapFilter update-items-by-plain-map-filter-var})]
      (is (= {:objects
              {:Mutation {:fields {:updateItemsByPlainMapFilter
                                   {:type '(non-null Boolean)
                                    :args {:config {:type '(non-null {:filter {:type (non-null String)}
                                                                      :limit {:type (non-null Int)}})}}}}}}}
             result)
          "Should compile mutation with an anonymous map argument, inlining its fields. No separate input object should be created."))))

(deftest ->graphql-type-map-method-test
  (testing ":map method of ->graphql-type multimethod for interfaces and implementations"
    (testing "compiling a schema marked as a GraphQL interface"
      (let [collector (atom {})
            ;; Using MapTestInterfaceSchema defined above
            result (binding [sut/*type-collector* collector
                             sut/*object-type* :objects] ; Default for top-level walk
                     (mc/walk MapTestInterfaceSchema sut/->graphql-type))]
        (is (= '(non-null :MapTestInterface) result)
            "Should return a non-null reference to the interface type name.")
        (is (= {:interfaces {:MapTestInterface {:fields {:id {:type '(non-null Uuid)}
                                                         :interfaceField {:type '(non-null String)}}}}}
               @collector)
            "The interface schema should be collected under :interfaces in the type collector with camelCase field names.")))

    (testing "compiling a schema that implements one or more GraphQL interfaces"
      (let [collector (atom {})
            ;; Using MapTestImplementingType, MapTestInterfaceOne, MapTestInterfaceTwo defined above
            result (binding [sut/*type-collector* collector
                             sut/*object-type* :objects]
                     (mc/walk MapTestImplementingType sut/->graphql-type))]

        (is (= '(non-null :MapTestImplementingType) result)
            "Should return a non-null reference to the implementing type name.")

        (is (= {:objects {:MapTestImplementingType {:fields {:id {:type '(non-null Uuid)}
                                                             :commonFieldA {:type '(non-null Int)}
                                                             :commonFieldB {:type '(non-null Boolean)}
                                                             :uniqueTypeField {:type '(non-null String)}}
                                                    :implements [:MapTestInterfaceOne :MapTestInterfaceTwo]}}
                :interfaces {:MapTestInterfaceOne {:fields {:commonFieldA {:type '(non-null Int)}}}
                             :MapTestInterfaceTwo {:fields {:commonFieldB {:type '(non-null Boolean)}}}}}
               @collector)
            "The implementing type should be collected under :objects with an :implements key and camelCase field names. Implemented interfaces also collected.")))

    (testing "compiling a schema that implements an interface registered in malli registry"
      (let [collector (atom {})
            type-registry-backup @registry/type-registry
            interface-key ::MapTestRegisteredInterfaceSchema]
        (try
          ;; Register the interface schema (MapTestRegisteredInterfaceSchema defined above)
          (registry/register-type! interface-key MapTestRegisteredInterfaceSchema)

          ;; Using MapTestImplementingTypeViaRegistered defined above
          (let [result (binding [sut/*type-collector* collector
                                 sut/*object-type* :objects]
                         (mc/walk MapTestImplementingTypeViaRegistered sut/->graphql-type))]

            (is (= '(non-null :MapTestImplementingTypeViaRegistered) result))
            (is (= {:objects {:MapTestImplementingTypeViaRegistered
                              {:fields {:id {:type '(non-null Uuid)}
                                        :registryCommonField {:type '(non-null String)}}
                               :implements [:MapTestRegisteredInterface]}}
                    :interfaces {:MapTestRegisteredInterface
                                 {:fields {:registryCommonField {:type '(non-null String)}}}}}
                   @collector)
                "Should correctly resolve registered interface, add to :implements list, and use camelCase field names."))
          (finally
            (reset! registry/type-registry type-registry-backup)))))

    (testing "compiling a plain map schema (no :graphql/type, e.g., for arguments)"
      (let [collector (atom {})
            ;; Using MapTestPlainMapSchema defined above
            result (binding [sut/*type-collector* collector
                             sut/*object-type* :objects] ; Does not affect this path
                     (mc/walk MapTestPlainMapSchema sut/->graphql-type))]
        (is (= '(non-null {:plainFieldX {:type (non-null String)}
                           :plainFieldY {:type (non-null Boolean)}}) result)
            "Should return the compiled fields directly (wrapped in non-null) with camelCase field names.")
        (is (empty? @collector)
            "No named type should be added to the collector for a plain map schema without :graphql/type.")))

    (testing "compiling a :multi schema into a GraphQL union type"
      (let [collector (atom {})
            type-registry-backup @registry/type-registry]
        (try
          ;; Register member types for the multi schema, as mc/children on :multi schema returns keywords
          (registry/register-type! ::UnionMemberOneSchema UnionMemberOneSchema)
          (registry/register-type! ::UnionMemberTwoSchema UnionMemberTwoSchema)

          (let [result (binding [sut/*type-collector* collector
                                 sut/*object-type* :objects] ; Member types are objects
                         (mc/walk TestUnionViaMultiSchema sut/->graphql-type))]
            (is (= '(non-null :TestUnionViaMulti) result)
                "Should return a non-null reference to the union type name.")
            (is (= {:unions {:TestUnionViaMulti {:members [:UnionMemberOne :UnionMemberTwo]}}
                    :objects {:UnionMemberOne {:fields {:fieldAlpha {:type '(non-null String)}}}
                              :UnionMemberTwo {:fields {:fieldBeta {:type '(non-null Boolean)}}}}}
                   @collector)
                "The union schema should be collected under :unions, and its members (objects) under :objects with camelCase field names."))
          (finally
            (reset! registry/type-registry type-registry-backup)))))

    (testing "compiling a map schema with a :graphql/hidden field"
      (let [collector (atom {})
            result (binding [sut/*type-collector* collector
                             sut/*object-type* :objects]
                     (mc/walk MapWithHiddenFieldSchema sut/->graphql-type))]
        (is (= '(non-null :MapWithHiddenField) result))
        (is (= {:objects {:MapWithHiddenField
                          {:fields {:visibleField {:type '(non-null String)}
                                    :anotherVisibleField {:type '(non-null Boolean)}}}}} ; :hiddenField is excluded
               @collector)
            "The :graphql/hidden field should be excluded from the compiled type.")))

    (testing "compiling a map schema with a field using := schema"
      (let [collector (atom {})
            result (binding [sut/*type-collector* collector
                             sut/*object-type* :objects]
                     (mc/walk MapWithEqualsFieldSchema sut/->graphql-type))]
        (is (= '(non-null :MapWithEqualsField) result))
        (is (= {:objects {:MapWithEqualsField
                          {:fields {:normalField {:type '(non-null String)}
                                    :anotherNormalField {:type '(non-null Int)}}}}} ; :equalsField is excluded
               @collector)
            "Fields with := schema should be excluded from the compiled type.")))))

(deftest merge-tag-with-type-test
  (testing "with TestUnionViaMultiSchema (simulating a GraphQL Union)"
    (let [type-registry-backup @registry/type-registry]
      (try
        ;; Register member types as TestUnionViaMultiSchema's dispatch fn returns keywords
        ;; and its children are defined with keywords.
        (registry/register-type! ::UnionMemberOneSchema UnionMemberOneSchema)
        (registry/register-type! ::UnionMemberTwoSchema UnionMemberTwoSchema)

        (let [tagger-fn (sut/merge-tag-with-type TestUnionViaMultiSchema)
              instance1 {:field-alpha "data for one"}   ; dispatch-fn returns ::UnionMemberOneSchema
              instance2 {:field-beta true}]             ; dispatch-fn returns ::UnionMemberTwoSchema
          (is (= :UnionMemberOne (tagger-fn instance1))
              "Tagger should return :UnionMemberOne for instance1 based on dispatch to ::UnionMemberOneSchema.")
          (is (= :UnionMemberTwo (tagger-fn instance2))
              "Tagger should return :UnionMemberTwo for instance2 based on dispatch to ::UnionMemberTwoSchema."))
        (finally
          (reset! registry/type-registry type-registry-backup)))))

  (testing "with TestInterfaceMulti (simulating a GraphQL Interface)"
    (let [type-registry-backup @registry/type-registry]
      (try
        ;; TestInterfaceMulti dispatches to ::TestConcreteTypeA, ::TestConcreteTypeB, or ::TestInterfaceBase.
        ;; Its children are [::TestConcreteTypeA TestConcreteTypeA] and [::TestConcreteTypeB TestConcreteTypeB].
        (registry/register-type! ::TestConcreteTypeA TestConcreteTypeA)
        (registry/register-type! ::TestConcreteTypeB TestConcreteTypeB)
        ;; TestInterfaceBase is not a direct child in TestInterfaceMulti's definition,
        ;; so a dispatch to ::TestInterfaceBase is expected to yield nil from the tagger.
        ;; Also, TestConcreteTypeA/B are :merge schemas.
        (registry/register-type! ::TestInterfaceBase TestInterfaceBase) ;; Register for completeness if dispatch returns it.

        (let [tagger-fn (sut/merge-tag-with-type TestInterfaceMulti)
              instanceA {:fieldA 123}       ; dispatch-fn returns ::TestConcreteTypeA
              instanceB {:fieldB false}     ; dispatch-fn returns ::TestConcreteTypeB
              instanceBase {:other true}]   ; dispatch-fn returns ::TestInterfaceBase

          (is (= :TestConcreteTypeA (tagger-fn instanceA))
              "Tagger should return :TestConcreteTypeA for instanceA.")
          (is (= :TestConcreteTypeB (tagger-fn instanceB))
              "Tagger should return :TestConcreteTypeB for instanceB.")
          (is (nil? (tagger-fn instanceBase))
              "Tagger should return nil for instanceBase as ::TestInterfaceBase is not an explicit child in TestInterfaceMulti's definition for ->tag-map processing."))
        (finally
          (reset! registry/type-registry type-registry-backup))))))

(deftest ->query-test
  (testing "simple query with specified fields"
    (let [[query-str types-map] (sut/->query {:Query/me [Actor :id :user-name]})]
      (is (= "query { me { id userName } }" query-str))
      (is (= {"Actor" Actor} types-map))))

  (testing "simple query with all fields (no fields specified)"
    (let [[query-str types-map] (sut/->query {:Query/me [Actor]})]
      ;; TODO: why is this different on clj vs cljs
      #?(:clj (is (= "query { me { email userName id } }" query-str))
         :cljs (is (= "query { me { id userName email } }" query-str)))
      (is (= {"Actor" Actor} types-map))))

  (testing "query with schema provided directly (not in a vector) throws"
    (is #?(:clj (thrown-with-msg? clojure.lang.ExceptionInfo
                                  #"Unsupported form"
                                  (sut/->query {:Query/me Actor}))
           :cljs (thrown-with-msg? js/Error
                                   #"Unsupported form"
                                   (sut/->query {:Query/me Actor})))))

  (testing "query with nested fields"
    (let [[query-str types-map] (sut/->query {:Query/game [Game :id {:home-team [Team :name {:players [Actor :user-name]}]}]})]
      (is (= "query { game { id homeTeam { name players { userName } } } }" query-str))
      (is (= {"Game" Game "Team" Team "Actor" Actor} types-map))))

  (testing "multiple top-level queries"
    (let [[query-str types-map] (sut/->query {:Query/me [Actor :id] :Query/activeGame [Game :title]})]
      ;; Check for presence of parts due to map key order unpredictability in query string
      (is (str/starts-with? query-str "query { "))
      (is (str/includes? query-str "me { id }"))
      (is (str/includes? query-str "activeGame { title }"))
      (is (str/ends-with? query-str " }"))
      (is (= {"Actor" Actor "Game" Game} types-map))))

  (testing "query with optional nested field"
    (let [[query-str types-map] (sut/->query {:Query/game [Game :id :title {:away-team [Team :name]}]})]
      (is (= "query { game { id title awayTeam { name } } }" query-str))
      (is (= {"Game" Game "Team" Team} types-map))))

  (testing "empty query map"
    (let [[query-str types-map] (sut/->query {})]
      (is (= "query {  }" query-str)) ; Note: (str/join " " []) is "" -> "query {  }"
      (is (= {} types-map))))

  (testing "query for a field that is a list of objects"
    (let [[query-str types-map] (sut/->query {:Query/team [Team :id {:players [Actor :id :user-name]}]})]
      (is (= "query { team { id players { id userName } } }" query-str))
      (is (= {"Team" Team "Actor" Actor} types-map))))

  (testing "query with an operation name"
    (let [[query-str types-map] (sut/->query {:Query/me [Actor :id]} "GetMyActor")]
      (is (= "query GetMyActor { me { id } }" query-str)
          "Should prefix the query with 'query OperationName'")
      (is (= {"Actor" Actor} types-map)
          "Types map should be correctly generated even with an operation name")))

  (testing "query with variable definitions using the third argument of ->query"
    (let [[query-str types-map] (sut/->query
                                 {:Query/user [Actor :id :user-name]} ; Query body
                                 "GetUserWithVars" ; Operation name
                                  ;; Variable definitions: var-name (keyword) -> malli-type-schema
                                 [[:userId :string] [:limit :int]])]
      (is (str/starts-with? query-str "query GetUserWithVars("))
      (is (str/ends-with? query-str ") { user { id userName } }"))

      ;; Extract the variable definitions part for a more robust check against order variations
      (let [prefix "query GetUserWithVars("
            suffix ") { user { id userName } }"
            var-defs-part (when (and (str/starts-with? query-str prefix)
                                     (str/ends-with? query-str suffix)
                                     (> (count query-str) (+ (count prefix) (count suffix))))
                            (subs query-str (count prefix) (- (count query-str) (count suffix))))]
        (is (some? var-defs-part) "Query string structure is as expected to extract variable definitions.")
        (is (= "$userId: String!, $limit: Int!" var-defs-part)
            (str "Variable definitions part should be '$userId: String!, $limit: Int!' . Actual: " var-defs-part)))

      (is (= {"Actor" Actor} types-map)
          "Types map should be correctly generated even with variable definitions")))

  (testing "query with operation variable of a complex input type"
    (let [[query-str types-map] (sut/->query
                                 {:Query/processComplex (list [ComplexInputQueryResult :status] :payload)} ; Query body, field takes :payload arg
                                 "ProcessComplexDataOp" ; Operation name
                                 [[:payload ComplexInputTypeTest]])] ; Operation variable :payload of complex type
      ;; Expected: query ProcessComplexDataOp($payload: !ComplexInputTypeTest) { processComplex(payload: $payload) { status } }
      (is (= "query ProcessComplexDataOp($payload: ComplexInputTypeTest!) { processComplex(payload: $payload) { status } }" query-str)
          "Should correctly format operation variable of complex input type and use it in field argument.")
      (is (= {"ComplexInputQueryResult" ComplexInputQueryResult} types-map)
          "Types map should contain the output type from selection set, not the input type.")))

  (testing "type collection for complex nested schemas using pre-defined ComplexObject and SimpleObject"
    (let [[query-str types-map] (sut/->query {:Query/complex [ComplexObject :id {:simple [SimpleObject :name]}]})]
      (is (= "query { complex { id simple { name } } }" query-str))
      (is (= {"ComplexObject" ComplexObject "SimpleObject" SimpleObject} types-map))
      (is (= [:map {:graphql/type "SimpleObject"} [:id :int] [:name :string]]
             (get types-map "SimpleObject")))))

  (testing "type collection for schemas registered in malli registry"
    (let [type-registry-value @registry/type-registry]
      (try
        (registry/register-type! ::Actor Actor)
        (registry/register-type! ::Game Game)
        (registry/register-type! ::Team Team) ; Register Team as well for the nested case

        (testing "simple registered schema"
          (let [[query-str types-map] (sut/->query {:Query/me [::Actor :id]})]
            (is (= "query { me { id } }" query-str))
            (is (= {"Actor" ::Actor} types-map))))

        (testing "nested registered schemas"
          (let [[query-str types-map] (sut/->query {:Query/game [::Game :title {:home-team [::Team :name]}]})]
            (is (= "query { game { title homeTeam { name } } }" query-str))
            (is (= {"Game" ::Game "Team" ::Team} types-map))))
        (finally
          (reset! registry/type-registry type-registry-value)))))

  (testing "schema without :graphql/type property"
    #?(:clj
       (is (thrown-with-msg? NullPointerException
                             #"Cannot invoke \"clojure.lang.Named.getName\(\)\" because \"x\" is null"
                             (sut/->query {:Query/data [SchemaWithoutGraphQLType :id]}))
           "In Clojure, (name nil) from (->graphql-type-name schema) returning nil then (name nil) throws NPE.")
       :cljs
       (is (thrown-with-msg? js/Error
                             #"Doesn't support name:"
                             (sut/->query {:Query/data [SchemaWithoutGraphQLType :id]}))
           "In CLJS, (name nil) will result in a does not support name")))

  (testing "query with field arguments"
    (testing "field arguments correctly formatted without explicit operation vars"
      (let [[query-str types-map] (sut/->query
                                   {:Query/userById (list [Actor :id :user-name] :userIdParam) ; Field `userById` takes arg `userIdParam`, selects :id, :user-name
                                    :Query/searchItems (list [SimpleObject :id] :filter :maxCount)} ; Field `searchItems` takes args `filter`, `maxCount`, selects :id
                                   "GenericOp")] ; Operation name
        (is (str/starts-with? query-str "query GenericOp { "))
        (is (str/ends-with? query-str " }"))
        ;; Check for field parts; their order in the query can vary
        (is (str/includes? query-str "userById(userIdParam: $userIdParam) { id userName }")
            "Should format field arguments like fieldName(argName: $argName) and include specified fields for userById.")
        (is (str/includes? query-str "searchItems(filter: $filter, maxCount: $maxCount) { id }")
            "Should format multiple field arguments correctly and include specified fields for searchItems.")
        (is (= {"Actor" Actor "SimpleObject" SimpleObject} types-map)
            "Types map should be correctly generated.")))

    (testing "field arguments linked to operation variables"
      (let [[query-str types-map] (sut/->query
                                   {:Query/userById (list [Actor :id :user-name] :userId)} ; Field `userById` takes arg `userId`
                                   "GetUserByIdOp" ; Operation name
                                   [[:userId :string] [:limit :int]])] ; Operation variables, :userId matches field arg, :limit is extra
        ;; Expected query structure: query GetUserByIdOp($userId: !String, $limit: !Int) { userById(userId: $userId) { id userName } }
        ;; The order of $userId and $limit in the var definition part can vary.
        (is (str/starts-with? query-str "query GetUserByIdOp("))
        (is (str/ends-with? query-str ") { userById(userId: $userId) { id userName } }"))

        ;; Extract and check the variable definitions part robustly
        (let [prefix "query GetUserByIdOp("
              suffix ") { userById(userId: $userId) { id userName } }"
              var-defs-part (when (and (str/starts-with? query-str prefix)
                                       (str/ends-with? query-str suffix)
                                       (> (count query-str) (+ (count prefix) (count suffix))))
                              (subs query-str (count prefix) (- (count query-str) (count suffix))))]
          (is (some? var-defs-part) "Query string structure should allow extraction of variable definitions.")
          (is (= "$userId: String!, $limit: Int!" var-defs-part)))
        (is (= {"Actor" Actor} types-map)
            "Types map should be correctly generated."))))

  (testing "query with inline fragments for different types (multi-schema using local test schemas)"
    (let [type-registry-value @registry/type-registry]
      (try
        ;; Register the local test schemas
        (registry/register-type! ::TestInterfaceMulti TestInterfaceMulti)
        (registry/register-type! ::TestConcreteTypeA TestConcreteTypeA)
        (registry/register-type! ::TestConcreteTypeB TestConcreteTypeB)
        ;; Note: TestInterfaceBase doesn't need explicit registration if only used via :merge

        (testing "selecting common fields and specific fields via nested vectors"
          (let [[query-str types-map] (sut/->query
                                       {:Query/interfaceQuery [::TestInterfaceMulti ; Use the multi-schema
                                                               :commonField ; Common field
                                                               ;; Specific fields per type
                                                               [::TestConcreteTypeA :fieldA]
                                                               [::TestConcreteTypeB :fieldB]]})]
            ;; Check query structure
            (is (str/starts-with? query-str "query { interfaceQuery { commonField "))
            (is (str/includes? query-str "... on TestConcreteTypeA { fieldA }"))
            (is (str/includes? query-str "... on TestConcreteTypeB { fieldB }"))
            (is (str/ends-with? query-str " } }"))
            ;; Check collected types
            (is (= {"TestInterfaceMulti" ::TestInterfaceMulti
                    "TestConcreteTypeA" ::TestConcreteTypeA
                    "TestConcreteTypeB" ::TestConcreteTypeB}
                   types-map)
                "Should collect the interface/union type and the specific concrete types")))

        (testing "selecting only specific fields via nested vectors"
          (let [[query-str types-map] (sut/->query
                                       {:Query/interfaceQuery [::TestInterfaceMulti
                                                               ;; No common fields selected directly
                                                               [::TestConcreteTypeA :commonField :fieldA] ; Common selected within type
                                                               [::TestConcreteTypeB :fieldB]]})]
            (is (str/starts-with? query-str "query { interfaceQuery { "))
            (is (str/includes? query-str "... on TestConcreteTypeA { commonField fieldA }"))
            (is (str/includes? query-str "... on TestConcreteTypeB { fieldB }"))
            (is (str/ends-with? query-str " } }"))
            (is (= {"TestInterfaceMulti" ::TestInterfaceMulti
                    "TestConcreteTypeA" ::TestConcreteTypeA
                    "TestConcreteTypeB" ::TestConcreteTypeB}
                   types-map))))

        (testing "selecting only common fields (no inline fragments needed)"
          (let [[query-str types-map] (sut/->query {:Query/interfaceQuery [::TestInterfaceMulti :commonField]})]
            (is (= "query { interfaceQuery { commonField } }" query-str))
            (is (= {"TestInterfaceMulti" ::TestInterfaceMulti} types-map))))

        (finally
          ;; Restore original registry state
          (reset! registry/type-registry type-registry-value))))))
