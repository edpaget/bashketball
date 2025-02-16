(ns app.models.dynamo-adapter-test
  (:require [app.models.dynamo-adapter :as sut]
            [clojure.test :as t]
            [malli.core :as m]))

(def TestSchema
  [:map {:pk [:int-key]
         :sk [:string-key :bool-key]}
   [:string-key :string]
   [:bool-key :boolean]
   [:int-key :int]
   [:double-key :double]])

(t/deftest decode-from-dynamo
  (let [dynamo-encoded {"string-key" {:S "string-value"}
                        "bool-key" {:BOOL true}
                        "int-key" {:N "123123"}
                        "double-key" {:N "123123.123123"}
                        "pk" {:S "int-key:123123"}
                        "sk" {:S "string-key:string-value#bool-key:true"}}
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
               (get dynamo-encoded "bool-key")))
      (t/is (= {:N "123123"}
               (get dynamo-encoded "int-key")))
      (t/is (= {:N "123123.123123"}
               (get dynamo-encoded "double-key")))
      (t/is (= {:S "string-value"}
               (get dynamo-encoded "string-key")))
      (t/is (= {:S "int-key:123123"}
               (get dynamo-encoded "pk")))
      (t/is (= {:S "string-key:string-value#bool-key:true"}
               (get dynamo-encoded "sk"))))))
