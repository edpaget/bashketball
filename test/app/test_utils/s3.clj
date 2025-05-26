(ns app.test-utils.s3
  (:require
   [app.s3 :as s3]
   [cognitect.aws.client.api :as aws]
   [cognitect.aws.credentials :as creds]))

;; --- Dynamic Vars for Test State ---
(def ^:dynamic *test-bucket-name* nil)
(def ^:dynamic *localstack-s3-client* nil) ; S3 client for LocalStack

;; --- S3 Test Fixture Utilities (adapted from app.s3-test) ---
(def test-s3-region "us-east-1")

(defn localstack-creds-provider []
  (creds/basic-credentials-provider
   {:access-key-id "test"
    :secret-access-key "test"}))

(defn- create-test-s3-client []
  (aws/client {:api :s3
               :endpoint-override {:protocol :http
                                   :hostname "localhost"
                                   :port 4566}
               :region test-s3-region
               :credentials-provider (localstack-creds-provider)}))

(defn- create-test-bucket! [client bucket-name]
  (let [response (aws/invoke client {:op :CreateBucket :request {:Bucket bucket-name}})]
    (when (or (:ErrorResponse response) (:cognitect.anomalies/category response))
      (throw (ex-info (str "Failed to create S3 bucket: " bucket-name) {:response response})))))

(defn- delete-all-objects! [client bucket-name]
  (let [list-response (aws/invoke client {:op :ListObjectsV2 :request {:Bucket bucket-name}})]
    (when (or (:ErrorResponse list-response) (:cognitect.anomalies/category list-response))
      (throw (ex-info (str "Failed to list objects in bucket " bucket-name) {:response list-response})))
    (when-let [contents (seq (:Contents list-response))]
      (let [objects-to-delete (mapv #(select-keys % [:Key]) contents)
            delete-response (aws/invoke client {:op :DeleteObjects
                                                :request {:Bucket bucket-name
                                                          :Delete {:Objects objects-to-delete :Quiet true}}})]
        (when (or (:ErrorResponse delete-response) (:cognitect.anomalies/category delete-response) (seq (:Errors delete-response)))
          (throw (ex-info (str "Failed to delete objects from bucket " bucket-name) {:response delete-response})))))))

(defn- delete-test-bucket! [client bucket-name]
  (try
    (delete-all-objects! client bucket-name)
    (let [response (aws/invoke client {:op :DeleteBucket :request {:Bucket bucket-name}})]
      (when (or (:ErrorResponse response) (:cognitect.anomalies/category response))
        (throw (ex-info (str "Failed to delete S3 bucket: " bucket-name) {:response response}))))
    (catch Exception e
      (println (str "Warning: Non-critical error during S3 bucket cleanup (" bucket-name "): " (.getMessage e)
                    (when-let [data (ex-data e)] (str " Data: " data)))))))

(defn s3-test-fixture [f]
  (let [bucket-name-prefix "asset-test-bucket-"
        dynamic-bucket-name (str bucket-name-prefix (System/currentTimeMillis) "-" (rand-int 10000))
        s3-client (create-test-s3-client)]
    (try
      (create-test-bucket! s3-client dynamic-bucket-name)
      (binding [s3/*s3-client* {:client s3-client :bucket-name dynamic-bucket-name} ; For app.s3 functions
                *localstack-s3-client* s3-client ; For direct use in tests if needed
                *test-bucket-name* dynamic-bucket-name]
        (f))
      (catch Exception e
        (println "Critical error during S3 test setup or execution:" (.getMessage e) (ex-data e))
        (throw e))
      (finally
        (delete-test-bucket! s3-client dynamic-bucket-name)))))
