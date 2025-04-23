(ns app.graphql.schema-transformer-test
  (:require
   [app.graphql.schema-transformer :as sut]
   #?(:clj [clojure.test :as t]
                :cljs [cljs.test :as t :include-macros true])))

(t/deftest test-transform-malli-schema-to-graphql-schema
  (let [malli-schema [:map {:type "test-schema"}
                      [:test :string]]]
    (t/is (= {:objects {:TestSchema {:fields {:test {:type '(non-null String)}}}}}
             (sut/malli-schema->graphql-schema malli-schema)))))
