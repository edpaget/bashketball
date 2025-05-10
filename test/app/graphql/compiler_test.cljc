(ns app.graphql.compiler-test
  (:require
   #?(:clj [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test :refer [deftest is testing] :include-macros true])
   [app.graphql.compiler :as sut]
   [clojure.string :as str]
   [app.registry :as registry]))

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

;; Define vars with schemas in metadata for testing
(def ^{:schema [:=> [:cat :any [:map [:id :int]] :any] [:maybe SimpleObject]]}
  get-simple-object-var nil)

(def ^{:schema [:=> [:cat :any :any :any] [:maybe ComplexObject]]}
  get-complex-object-var nil)

(def ^{:schema [:=> [:cat :any [:map [:name :string]] :any] :string]}
  create-simple-object-var nil)

(def ^{:schema [:=> [:cat :any :any :any] [:vector :string]]}
  get-list-of-strings-var nil)

(def ^{:schema [:=> [:cat :any :any :any] [:maybe :int]]}
  get-optional-int-var nil)

;; --- Tests ---

;; TODO: moves this function and tests to a new clj only namespace. The var metadata technique does not
;; work in cljs because vars are not reified
#?(:clj
   (deftest name->var->graphql-schema-test
     (testing "empty input map"
       (is (= {} (sut/name->var->graphql-schema {}))
           "Should return an empty map for empty input"))

     (testing "single query field with simple return type (vector of strings)"
       (let [result (sut/name->var->graphql-schema {:Query/getListOfStrings #'get-list-of-strings-var})]
         (is (= {:objects
                 {:Query {:fields {:getListOfStrings {:type '(list (non-null String))}}}}}
                result)
             "Should correctly compile a query returning a list of non-null strings")))

     (testing "single query field with optional scalar return type"
       (let [result (sut/name->var->graphql-schema {:Query/getOptionalInt #'get-optional-int-var})]
         (is (= {:objects
                 {:Query {:fields {:getOptionalInt {:type 'Int}}}}} ; :maybe :int becomes Int
                result)
             "Should correctly compile a query returning an optional Int")))

     (testing "single query field with object return type and arguments"
       (let [result (sut/name->var->graphql-schema {:Query/getSimpleObject #'get-simple-object-var})]
         (is (= {:objects
                 {:Query {:fields {:getSimpleObject {:type :SimpleObject ; Return type name
                                                     :fields {:id {:type '(non-null Int)}}}}} ; Argument type
                  ;; The SimpleObject type definition should also be collected
                  :SimpleObject {:fields {:id {:type '(non-null Int)}
                                          :name {:type '(non-null String)}}}}}
                result)
             "Should compile query with args, object return type, and collect the object type")))

     (testing "single mutation field with arguments and simple return type"
       (let [result (sut/name->var->graphql-schema {:Mutation/createSimpleObject #'create-simple-object-var})]
         (is (= {:objects
                 {:Mutation {:fields {:createSimpleObject {:type '(non-null String) ; Return type
                                                           :fields {:name {:type '(non-null String)}}}}}}} ; Argument type
                result)
             "Should correctly compile a mutation with args and a simple return type")))

     (testing "query with complex nested object return type"
       (let [result (sut/name->var->graphql-schema {:Query/getComplexObject #'get-complex-object-var})]
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
             "Should compile query with complex object return type and collect all nested types")))

     (testing "multiple query and mutation fields"
       (let [result (sut/name->var->graphql-schema
                     {:Query/getSimpleObject #'get-simple-object-var
                      :Query/getListOfStrings #'get-list-of-strings-var
                      :Mutation/createSimpleObject #'create-simple-object-var})]
         (is (= {:objects
                 {:Query {:fields {:getSimpleObject {:type :SimpleObject
                                                     :fields {:id {:type '(non-null Int)}}}
                                   :getListOfStrings {:type '(list (non-null String))}}}
                  :Mutation {:fields {:createSimpleObject {:type '(non-null String)
                                                           :fields {:name {:type '(non-null String)}}}}}
                  ;; SimpleObject type collected once
                  :SimpleObject {:fields {:id {:type '(non-null Int)}
                                          :name {:type '(non-null String)}}}}}
                result)
             "Should correctly compile multiple queries and mutations, collecting types")))))

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

  (testing "query with arguments (feature currently has limitations in string generation)"
    ;; This tests the current behavior where argument stringification is not implemented,
    ;; leading to an error when ->graphql-string receives a raw map instead of an AST node.
    (is (thrown-with-msg? #?(:clj IllegalArgumentException :cljs js/Error)
                          #"No method in multimethod "
                          (sut/->query '({:Query/userById [Actor :id]} :id "user-123"))))))
