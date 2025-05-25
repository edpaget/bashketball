(ns app.s3-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [app.s3 :as s3]
   [cognitect.aws.client.api :as aws]
   [clojure.java.io :as io]
   [cognitect.aws.credentials :as creds]))

;; --- LocalStack Configuration ---
(def ^:private test-region "us-east-1")

;; --- Dynamic Vars for Test State ---
;; These will be bound by the fixture for use in tests
(def ^:dynamic ^:private *test-bucket-name* nil)
(def ^:dynamic ^:private *localstack-client* nil)

;; --- Helper Functions for S3 Operations against LocalStack ---
(defn- localstack-creds-provider []
  (creds/basic-credentials-provider
   {:access-key-id "test"    ; LocalStack uses dummy credentials
    :secret-access-key "test"}))

(defn- create-test-bucket! [client bucket-name]
  (let [response (aws/invoke client {:op :CreateBucket
                                     :request {:Bucket bucket-name}})]
    (when (or (:ErrorResponse response) (:cognitect.anomalies/category response))
      (throw (ex-info (str "Failed to create S3 bucket: " bucket-name)
                      {:response response})))))

(defn- delete-all-objects! [client bucket-name]
  (let [list-response (aws/invoke client {:op :ListObjectsV2
                                          :request {:Bucket bucket-name}})]
    (when (or (:ErrorResponse list-response) (:cognitect.anomalies/category list-response))
      (println (str "Warning: Failed to list objects in bucket " bucket-name " for deletion. Response: " list-response))
      (throw (ex-info (str "Failed to list objects in bucket " bucket-name) {:response list-response})))

    (when-let [contents (seq (:Contents list-response))]
      (let [objects-to-delete (mapv #(select-keys % [:Key]) contents)
            delete-response (aws/invoke client {:op :DeleteObjects
                                                :request {:Bucket bucket-name
                                                          :Delete {:Objects objects-to-delete
                                                                   :Quiet true}}})]
        (when (or (:ErrorResponse delete-response) (:cognitect.anomalies/category delete-response))
          (throw (ex-info (str "Failed to submit DeleteObjects request for bucket " bucket-name)
                          {:response delete-response})))
        (when-let [errors (seq (:Errors delete-response))]
          (throw (ex-info (str "Errors occurred while deleting objects from bucket " bucket-name)
                          {:errors errors :response delete-response})))))))

(defn- delete-test-bucket! [client bucket-name]
  (try
    (delete-all-objects! client bucket-name)
    (let [response (aws/invoke client {:op :DeleteBucket
                                       :request {:Bucket bucket-name}})]
      (when (or (:ErrorResponse response) (:cognitect.anomalies/category response))
        (throw (ex-info (str "Failed to delete S3 bucket: " bucket-name)
                        {:response response}))))
    (catch Exception e
      (println (str "Warning: Non-critical error during S3 bucket cleanup (" bucket-name "): " (.getMessage e)
                    (when-let [data (ex-data e)] (str " Data: " data)))))))

;; --- Test Fixture ---
(defn s3-fixture [f]
  (let [bucket-name-prefix "app-s3-test-"
        dynamic-bucket-name (str bucket-name-prefix (System/currentTimeMillis) "-" (rand-int 10000))
        client (aws/client {:api :s3
                            :endpoint-override {:protocol :http
                                                :hostname "localhost"
                                                :port 4566}
                            :region test-region
                            :credentials-provider (localstack-creds-provider)})]
    (try
      (create-test-bucket! client dynamic-bucket-name)
      (binding [s3/*s3-client* {:client client :bucket-name dynamic-bucket-name}
                *test-bucket-name* dynamic-bucket-name
                *localstack-client* client]
        (f))
      (catch Exception e
        (println "Critical error during S3 test setup or execution:" (.getMessage e) (ex-data e))
        (throw e))
      (finally
        (delete-test-bucket! client dynamic-bucket-name)))))

(use-fixtures :once s3-fixture)

;; --- Tests for app.s3/put-object ---
(deftest put-object-test
  (testing "putting an object using dynamic *s3-client* with ContentType"
    (let [test-key (str "test-object-dynamic-" (random-uuid) ".txt")
          test-body "Hello from dynamic client with ContentType!"
          opts {:ContentType "text/plain; charset=utf-8"}
          response (s3/put-object test-key test-body opts)]
      (is (nil? (:ErrorResponse response)) (str "PutObject failed: " response))
      (is (nil? (:cognitect.anomalies/category response)) (str "PutObject failed with anomaly: " response))
      (is (some? (:ETag response)) "Response should contain ETag")

      (let [retrieved-obj-response (s3/get-object test-key)]
        (is (nil? (:ErrorResponse retrieved-obj-response)) (str "GetObject failed: " retrieved-obj-response))
        (is (nil? (:cognitect.anomalies/category retrieved-obj-response)) (str "GetObject failed with anomaly: " retrieved-obj-response))
        (is (= test-body (slurp (:Body retrieved-obj-response))))
        (is (= "text/plain; charset=utf-8" (:ContentType retrieved-obj-response))))))

  (testing "putting an object using explicit client map with Tagging"
    (let [client-map {:client *localstack-client* :bucket-name *test-bucket-name*}
          test-key (str "test-object-explicit-" (random-uuid) ".txt")
          test-body "Hello from explicit client with Tagging!"
          opts {:Tagging "app=test&env=dev"}
          response (s3/put-object client-map test-key test-body opts)]
      (is (nil? (:ErrorResponse response)) (str "PutObject failed: " response))
      (is (nil? (:cognitect.anomalies/category response)) (str "PutObject failed with anomaly: " response))
      (is (some? (:ETag response)) "Response should contain ETag")

      (let [retrieved-obj-response (s3/get-object client-map test-key)
            retrieved-body (slurp (:Body retrieved-obj-response))]
        (is (nil? (:ErrorResponse retrieved-obj-response)) (str "GetObject failed: " retrieved-obj-response))
        (is (nil? (:cognitect.anomalies/category retrieved-obj-response)) (str "GetObject failed with anomaly: " retrieved-obj-response))
        (is (= test-body retrieved-body)))

      (let [tagging-response (aws/invoke *localstack-client*
                                         {:op :GetObjectTagging
                                          :request {:Bucket *test-bucket-name* :Key test-key}})]
        (is (nil? (:ErrorResponse tagging-response)) (str "GetObjectTagging failed: " tagging-response))
        (is (nil? (:cognitect.anomalies/category tagging-response)) (str "GetObjectTagging failed with anomaly: " tagging-response))
        (is (= [{:Key "app" :Value "test"} {:Key "env" :Value "dev"}]
               (sort-by :Key (:TagSet tagging-response)))))))

  (testing "putting an object with InputStream body and no extra opts"
    (let [test-key (str "test-object-inputstream-" (random-uuid) ".dat")
          test-string "Hello from InputStream!"
          test-body (io/input-stream (.getBytes test-string "UTF-8"))
          opts {}
          response (s3/put-object test-key test-body opts)]
      (is (nil? (:ErrorResponse response)) (str "PutObject failed: " response))
      (is (nil? (:cognitect.anomalies/category response)) (str "PutObject failed with anomaly: " response))
      (is (some? (:ETag response)) "Response should contain ETag")

      (let [retrieved-obj-response (s3/get-object test-key)
            retrieved-body (slurp (:Body retrieved-obj-response))]
        (is (nil? (:ErrorResponse retrieved-obj-response)) (str "GetObject failed: " retrieved-obj-response))
        (is (nil? (:cognitect.anomalies/category retrieved-obj-response)) (str "GetObject failed with anomaly: " retrieved-obj-response))
        (is (= test-string retrieved-body))))))
