(ns app.graphql.compiler-test
  (:require
   #?(:clj [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test :refer [deftest is testing] :include-macros true])
   [app.graphql.compiler :as sut]
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

;; Define vars with schemas in metadata for testing
(def ^{:schema [:=> [:cat :any [:map [:id :int]] :any] [:maybe SimpleObject]]}
  get-simple-object-var nil)

(def ^{:schema [:=> [:cat :any :any :any] [:maybe ComplexObject]]}
  get-complex-object-var nil)

(def ^{:schema [:=> [:cat :any [:map [:name :string]] :any] :string]}
  create-simple-object-var nil)

(def ^{:schema (mc/schema [:=> [:cat :any :any :any] [:vector :string]])}
  get-list-of-strings-var nil)

(def ^{:schema [:=> [:cat :any :any :any] [:maybe :int]]}
  get-optional-int-var nil)

;; --- Tests ---

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
          "Should correctly compile multiple queries and mutations, collecting types"))))
