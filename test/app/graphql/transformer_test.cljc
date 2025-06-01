(ns app.graphql.transformer-test
  #?(:cljs (:require-macros [cljs.test :refer [deftest is testing]]))
  (:require
   #?(:clj [clojure.test :refer [deftest is testing]]
      :cljs [cljs.test :refer [deftest is testing]]) ; Note: cljs.test macros are referred above
   [app.graphql.transformer :as sut]
   #?(:cljs [cljs.core :as cljs]))) ; For random-uuid in CLJS

;; --- Test Schemas ---
(def SimpleMapSchema
  [:map
   [:first-name :string]
   [:last-name {:optional true} :string]])

(def EnumSchema
  [:map
   [:status [:enum :order-status/PENDING :order-status/SHIPPED :order-status/DELIVERED]]
   [:priority {:optional true} [:enum :priority/LOW :priority/HIGH]]])

(def KebabKeyEnumSchema
  [:map
   [:order-status [:enum :os/PENDING :os/CONFIRMED]]])

(def NestedMapSchema
  [:map
   [:user-id :int]
   [:profile-data
    [:map
     [:display-name :string]
     [:contact-email :string]]]])

(def CombinedSchema
  [:map
   [:item-id :uuid]
   [:item-name :string]
   [:item-status [:enum :item-status/AVAILABLE :item-status/SOLD-OUT]]
   [:dimensions-cm
    [:map
     [:height-cm :int]
     [:width-cm :int]]]])

(def NonKeywordKeySchema [:map [:a :string] ["string-key" :int]])

(def DefaultValuesSchema
  [:map
   [:id :int]
   [:name {:default "Unknown"} :string]
   [:active? {:default true} :boolean]
   [:role {:default :user.role/viewer} [:enum :user.role/admin :user.role/editor :user.role/viewer]]])

;; --- Test Cases for sut/encode ---

(deftest encode-test
  (testing "encodes simple map with kebab-case keys to camelCase keyword keys"
    (let [model SimpleMapSchema
          data {:first-name "John" :last-name "Doe"}
          expected {:firstName "John" :lastName "Doe"}]
      (is (= expected (sut/encode data model)))))

  (testing "encodes map with enum values (namespaced keywords) to string representations"
    (let [model EnumSchema
          data {:status :order-status/SHIPPED}
          expected {:status "SHIPPED"}] ; :status key is already "camelCase-like"
      (is (= expected (sut/encode data model)))))

  (testing "encodes map with a kebab-case key for an enum field"
    (let [model KebabKeyEnumSchema
          data {:order-status :os/PENDING}
          expected {:orderStatus "PENDING"}] ; :order-status -> :orderStatus, :os/PENDING -> "PENDING"
      (is (= expected (sut/encode data model)))))

  (testing "encodes nested map with kebab-case keys"
    (let [model NestedMapSchema
          data {:user-id 123
                :profile-data {:display-name "tester"
                               :contact-email "test@example.com"}}
          expected {:userId 123
                    :profileData {:displayName "tester"
                                  :contactEmail "test@example.com"}}]
      (is (= expected (sut/encode data model)))))

  (testing "encodes a comprehensive schema with kebab-keys, enums, and nested maps"
    (let [item-uuid #?(:clj (java.util.UUID/randomUUID) :cljs (cljs/random-uuid))
          model CombinedSchema
          data {:item-id item-uuid
                :item-name "Test Item"
                :item-status :item-status/AVAILABLE
                :dimensions-cm {:height-cm 10 :width-cm 20}}
          expected {:itemId item-uuid
                    :itemName "Test Item"
                    :itemStatus "AVAILABLE"
                    :dimensionsCm {:heightCm 10 :widthCm 20}}]
      (is (= expected (sut/encode data model)))))

  (testing "handles nil values for optional fields during encoding"
    (let [model SimpleMapSchema
          data {:first-name "John" :last-name nil}
          expected {:firstName "John" :lastName nil}]
      (is (= expected (sut/encode data model)))))

  (testing "handles missing optional fields (they are not included in output)"
    (let [model SimpleMapSchema
          data {:first-name "Jane"}
          expected {:firstName "Jane"}]
      (is (= expected (sut/encode data model)))))

  (testing "encodes an empty map"
    (let [model SimpleMapSchema
          data {}
          expected {}]
      (is (= expected (sut/encode data model)))))

  (testing "encodes map with extra keys not in schema (malli's default is to preserve and transform their keys)"
    (let [model SimpleMapSchema
          data {:first-name "John" :extra-field "value" :another-key 42}
          expected {:firstName "John" :extraField "value" :anotherKey 42}]
      (is (= expected (sut/encode data model)))))

  (testing "encodes map with string keys and non-kebab keyword keys from NonKeywordKeySchema"
    (let [model NonKeywordKeySchema
          data {:a "test" "string-key" 123}
          expected {:a "test" :stringKey 123}]
      (is (= expected (sut/encode data model))))))

;; --- Test Cases for sut/decode ---

(deftest decode-test
  (testing "decodes simple map with camelCase keys to kebab-case keyword keys"
    (let [model SimpleMapSchema
          data {:firstName "John" :lastName "Doe"}
          expected {:first-name "John" :last-name "Doe"}]
      (is (= expected (sut/decode data model)))))

  (testing "decodes map with string enum values to namespaced keywords"
    (let [model EnumSchema
          data {:status "SHIPPED" :priority "HIGH"}
          expected {:status :order-status/SHIPPED :priority :priority/HIGH}]
      (is (= expected (sut/decode data model)))))

  (testing "decodes map with a camelCase key for an enum field"
    (let [model KebabKeyEnumSchema ; Schema has :order-status, input has :orderStatus
          data {:orderStatus "PENDING"}
          expected {:order-status :os/PENDING}]
      (is (= expected (sut/decode data model)))))

  (testing "decodes nested map with camelCase keys"
    (let [model NestedMapSchema
          data {:userId 123
                :profileData {:displayName "tester"
                              :contactEmail "test@example.com"}}
          expected {:user-id 123
                    :profile-data {:display-name "tester"
                                   :contact-email "test@example.com"}}]
      (is (= expected (sut/decode data model)))))

  (testing "decodes a comprehensive schema with camelCase keys and string enums"
    (let [item-uuid #?(:clj (java.util.UUID/randomUUID) :cljs (cljs/random-uuid))
          model CombinedSchema
          data {:itemId item-uuid
                :itemName "Test Item"
                :itemStatus "AVAILABLE"
                :dimensionsCm {:heightCm 10 :widthCm 20}}
          expected {:item-id item-uuid
                    :item-name "Test Item"
                    :item-status :item-status/AVAILABLE
                    :dimensions-cm {:height-cm 10 :width-cm 20}}]
      (is (= expected (sut/decode data model)))))

  (testing "handles nil values for optional fields during decoding"
    (let [model SimpleMapSchema
          data {:firstName "John" :lastName nil}
          expected {:first-name "John" :last-name nil}]
      (is (= expected (sut/decode data model)))))

  (testing "handles missing optional fields (no defaults, should remain absent)"
    (let [model SimpleMapSchema
          data {:firstName "Jane"}
          expected {:first-name "Jane"}]
      (is (= expected (sut/decode data model)))))

  (testing "applies default values for missing fields"
    (let [model DefaultValuesSchema
          data {:id 1}
          expected {:id 1 :name "Unknown" :active? true :role :user.role/viewer}]
      (is (= expected (sut/decode data model)))))

  (testing "decodes an empty map"
    (let [model SimpleMapSchema
          data {}
          expected {}]
      (is (= expected (sut/decode data model)))))

  (testing "decodes map with extra camelCase keys not in schema (keys are transformed)"
    (let [model SimpleMapSchema
          data {:firstName "John" :extraField "value" :anotherKey 42}
          expected {:first-name "John" :extra-field "value" :another-key 42}]
      (is (= expected (sut/decode data model)))))

  (testing "decoding invalid enum string value (remains string, Malli validation will catch it)"
    (let [model EnumSchema
          data {:status "INVALID_STATUS"}
          expected {:status "INVALID_STATUS"}] ; enum transformer returns original if not valid
      (is (= expected (sut/decode data model)))))

  (testing "decoding with string keys in input data (string keys are transformed to kebab-case keywords)"
    (let [model NonKeywordKeySchema ; Schema is [:map [:a :string] ["string-key" :int]]
          data {:a "test" "stringKey" 123} ; Input has string key "stringKey"
          expected {:a "test" :string-key 123}] ; Expect kebab-case keyword :string-key
      (is (= expected (sut/decode data model))))))
