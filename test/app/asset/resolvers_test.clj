(ns app.asset.resolvers-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [clojure.string :as str]
   [app.asset]
   [app.s3 :as s3]
   [app.db :as db]
   [app.test-utils :as tu]
   [app.test-utils.s3 :as tu.s3]
   [app.graphql.resolvers :as gql.resolvers]
   [app.models :as models]
   [cognitect.aws.client.api :as aws]) ; Added for aws/client
  (:import
   [java.util Base64]))

(use-fixtures :once tu.s3/s3-test-fixture tu/db-fixture)
(use-fixtures :each tu/rollback-fixture)

(def ^:private resolver-fn (gql.resolvers/get-resolver-fn 'app.asset :Mutation/createAsset))
(def ^:private test-asset-base-path "test-assets")
(def ^:private mock-config {:game-assets {:asset-path test-asset-base-path}})

;; s3/*s3-client* is bound by s3-test-fixture.
;; The resolver schema requires :s3-client in context.
;; This function ensures the context captures the dynamically bound s3/*s3-client*.
(defn- get-test-context []
  {:config mock-config
   :s3-client s3/*s3-client*})

(deftest create-asset-resolver-success-test
  (testing "successful asset creation and upload"
    (let [mime-type "image/png"
          img-content-string "fake PNG data"
          img-blob (.encodeToString (Base64/getEncoder) (.getBytes img-content-string "UTF-8"))
          args {:mime-type mime-type :img-blob img-blob}
          result (resolver-fn (get-test-context) args nil)
          asset-id (:id result)
          expected-s3-key (str test-asset-base-path "/" asset-id)]

      (is (uuid? asset-id) "Result ID should be a UUID")
      (is (= mime-type (:mime-type result)))
      (is (str/includes? (:img-url result) test-asset-base-path))
      (is (= :game-asset-status-enum/UPLOADED (:status result)))
      (is (nil? (:error-message result)))

      ;; Verify database state
      (let [db-asset (db/execute-one! {:select [:*]
                                       :from   [(models/->table-name ::models/GameAsset)]
                                       :where  [:= :id asset-id]})]
        (is (some? db-asset) "Asset should exist in DB")
        (is (= asset-id (:id db-asset)))
        (is (= :game-asset-status-enum/UPLOADED (:status db-asset)))
        (is (= mime-type (:mime-type db-asset)))
        (is (= test-asset-base-path (:img-url db-asset))))

      ;; Verify S3 object
      (let [s3-object-response (s3/get-object s3/*s3-client* expected-s3-key)]
        (is (nil? (:ErrorResponse s3-object-response)) (str "S3 GetObject failed: " s3-object-response))
        (is (nil? (:cognitect.anomalies/category s3-object-response)) (str "S3 GetObject failed with anomaly: " s3-object-response))
        (is (= img-content-string (slurp (:Body s3-object-response))))))))

(deftest create-asset-resolver-failure-test
  (testing "asset creation with S3 upload failure"
    (let [mime-type (str "image/jpeg-" (random-uuid)) ; Unique mime-type for this test
          img-content-string "another fake JPEG image"
          img-blob (.encodeToString (Base64/getEncoder) (.getBytes img-content-string "UTF-8"))
          args {:mime-type mime-type :img-blob img-blob}
          ;; Configure a faulty S3 client that points to a dead endpoint
          faulty-aws-client (aws/client {:api :s3
                                         :endpoint-override {:protocol :http
                                                             :hostname "brokehost"
                                                             :port 12346} ; Arbitrary unused port
                                         ;; Assuming tu.s3 provides these or using defaults
                                         :region (or tu.s3/test-s3-region "us-east-1")
                                         :credentials-provider (tu.s3/localstack-creds-provider)})
          faulty-s3-client-map {:client faulty-aws-client
                                :bucket-name tu.s3/*test-bucket-name*}]

      ;; Ensure no asset with this mime-type exists before the test
      (is (empty? (db/execute! {:select [:*]
                                :from   [(models/->table-name ::models/GameAsset)]
                                :where  [:= :mime-type mime-type]}))
          "No asset with this mime-type should exist yet")

      (binding [s3/*s3-client* faulty-s3-client-map]
        (is (not (nil? (resolver-fn (get-test-context) args nil)))))

      (let [db-assets-after (db/execute! {:select [:*]
                                          :from   [(models/->table-name ::models/GameAsset)]
                                          :where  [:= :mime-type mime-type]})]
        (is (= 1 (count db-assets-after)) "One asset should have been created and marked as ERROR")
        (when-let [db-asset (first db-assets-after)]
          (let [asset-id (:id db-asset)]
            (is (uuid? asset-id))
            (is (= mime-type (:mime-type db-asset)))
            (is (= test-asset-base-path (:img-url db-asset)))
            (is (= :game-asset-status-enum/ERROR (:status db-asset)))
            (is (not (nil? (:error-message db-asset))))))))))
