(ns app.models.dynamo-adapter-test
  (:require [app.models.dynamo-adapter :as sut]
            [clojure.test :as t]
            [malli.core :as m]))

(def TestSchema
  [:map {:pk [:int-key]
         :sk [:string-key :bool-key]
         :type "test-schema"}
   [:created-at {:dynamo/on-create true
                 :default-now true} :time/instant]
   [:string-key :string]
   [:bool-key :boolean]
   [:int-key :int]
   [:double-key :double]])

(t/deftest decode-from-dynamo
  (let [dynamo-encoded {"string_key" {:S "string-value"}
                        "bool_key" {:BOOL true}
                        "int_key" {:N "123123"}
                        "double_key" {:N "123123.123123"}
                        "pk" {:S "int-key:123123"}
                        "sk" {:S "test-schema#string-key:string-value#bool-key:true"}}
        decoded (m/decode TestSchema dynamo-encoded sut/dynamo-transfomer)]
    (t/testing "decode"
      (t/is (= "string-value"
               (get decoded :string-key)))
      (t/is (= true
               (get decoded :bool-key)))
      (t/is (= 123123.123123
               (get decoded :double-key)))
      (t/is (= 123123
               (get decoded :int-key)))
      (t/is (nil? (get decoded "pk")))
      (t/is (nil? (get decoded "sk"))))))

(t/deftest encode-from-dynamo
  (let [decoded {:string-key "string-value"
                 :int-key 123123
                 :double-key 123123.123123
                 :bool-key true}
        dynamo-encoded (m/encode TestSchema decoded sut/dynamo-transfomer)]
    (t/testing "encode"
      (t/is (= {:BOOL true}
               (get dynamo-encoded ":bool_key")))
      (t/is (= {:N "123123"}
               (get dynamo-encoded ":int_key")))
      (t/is (= {:N "123123.123123"}
               (get dynamo-encoded ":double_key")))
      (t/is (= {:S "string-value"}
               (get dynamo-encoded ":string_key"))))))

(t/deftest encode-keys-from-dynamo
  (let [decoded {:string-key "string-value"
                 :int-key 123123
                 :double-key 123123.123123
                 :bool-key true}]
    (t/is (= {:Key {"pk" {:S "int-key:123123"}
                    "sk" {:S "test-schema#string-key:string-value#bool-key:true"}}}
             ((sut/key-builder TestSchema) decoded)))))

(t/deftest encode-update-expression-from-dynamo
  (let [decoded (m/decode TestSchema
                          {:string-key "string-value"
                           :int-key 123123
                           :double-key 123123.123123
                           :bool-key true}
                          sut/default-now-transformer)]
    (t/is (= {:UpdateExpression "SET string_key = :string_key, int_key = :int_key, double_key = :double_key, bool_key = :bool_key, created_at = if_not_exists(created_at, :created_at)"}
             ((sut/update-expression-builder TestSchema) decoded)))))
