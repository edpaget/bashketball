(ns app.models-test
  #?(:cljs (:require-macros [cljs.test :refer [deftest is testing]]))
  (:require
   #?(:clj [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test])
   [app.models :as models]
   [app.registry :as registry]))

(registry/defschema ::SchemaWithVector
  [:map
   [:name :string]
   [:tags {:description "A list of tags"} [:vector :string]]])

(registry/defschema ::SchemaWithMultipleVectors
  [:map
   [:tags [:vector :string]]
   [:scores [:vector :int]]])

(registry/defschema ::SchemaWithoutVector
  [:map
   [:name :string]
   [:age :int]])

(deftest ->pk-test
  (testing "Schema with custom ::pk defined"
    (is (= [:provider :provider_identity] (models/->pk ::models/Identity))
        "Should return the custom PK defined in the schema properties.")
    (is (= [:name :version] (models/->pk ::models/Card))
        "Should return the custom PK for ::Card."))

  (testing "Schema without custom ::pk defined"
    (is (= [:id] (models/->pk ::models/Actor))
        "Should default to [:id] when ::pk is not specified.")
    (is (= [:id] (models/->pk ::models/AppAuthorization))
        "Should default to [:id] for ::AppAuthorization."))

  (testing "Schema derived via :merge (inherits PK if not overridden)"
    ;; ::PlayerCard merges ::Card, so it should inherit ::Card's PK
    (is (= [:name :version] (models/->pk ::models/PlayerCard))
        "Should inherit PK from merged schema if not overridden.")
    ;; ::CardWithFate merges ::Card
    (is (= [:name :version] (models/->pk ::models/CardWithFate))
        "Should inherit PK from merged schema.")
    ;; ::SplitPlayCard merges ::CardWithFate which merges ::Card
    (is (= [:name :version] (models/->pk ::models/SplitPlayCard))
        "Should inherit PK through multiple merges."))

  (testing "Multi-schema (should probably use the base or common PK if applicable)"
    ;; ::GameCard is a multi-schema based on ::Card derivatives.
    (is (= [:name :version] (models/->pk ::models/GameCard))
        "Is hardcoded to [:name :version]")))

(deftest ->set-lift-test
  (testing "schema with a single vector field"
    (is (= #{:tags}
           (models/->set-lift ::SchemaWithVector))))

  (testing "schema with multiple vector fields should return the first one"
    (is (= #{:tags :scores}
           (models/->set-lift ::SchemaWithMultipleVectors))))

  (testing "schema with no vector fields"
    (is (= #{}
           (models/->set-lift ::SchemaWithoutVector))))

  (testing "with a complex schema from the app (PlayerCard)"
    (is (= #{:abilities}
           (models/->set-lift ::models/PlayerCard))))

  (testing "with a complex schema from the app with no vectors (Card)"
    (is (= #{}
           (models/->set-lift ::models/Card))))

  (testing "with a multi-schema"
    (is (= #{:abilities}
           (models/->set-lift ::models/GameCard)))))
