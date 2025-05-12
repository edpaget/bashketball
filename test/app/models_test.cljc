(ns app.models-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [app.models :as models]))

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
