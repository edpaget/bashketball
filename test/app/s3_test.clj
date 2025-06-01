(ns app.s3-test
  (:require
   [clojure.test :refer [deftest testing is use-fixtures]]
   [app.s3 :as s3]
   [app.test-utils.s3 :as tu.s3]
   [cognitect.aws.client.api :as aws]
   [clojure.java.io :as io]))

(use-fixtures :once tu.s3/s3-test-fixture)

;; --- Tests for app.s3/put-object ---
(deftest put-object-test
  (testing "putting an object using dynamic *s3-client* with ContentType"
    (let [test-key (str "test-object-dynamic-" (random-uuid) ".txt")
          test-body "Hello from dynamic client with ContentType!"
          opts {:ContentType "text/plain; charset=utf-8"}
          response (s3/put-object test-key test-body opts)]
      (is (some? (:ETag response)) "Response should contain ETag for successful PutObject")

      (let [retrieved-obj-response (s3/get-object test-key)]
        ;; If get-object failed, it would throw. Successful response is used directly.
        (is (= test-body (slurp (:Body retrieved-obj-response))))
        (is (= "text/plain; charset=utf-8" (:ContentType retrieved-obj-response))))))

  (testing "putting an object using explicit client map with Tagging"
    (let [client-map {:client tu.s3/*localstack-s3-client* :bucket-name tu.s3/*test-bucket-name*}
          test-key (str "test-object-explicit-" (random-uuid) ".txt")
          test-body "Hello from explicit client with Tagging!"
          opts {:Tagging "app=test&env=dev"}
          response (s3/put-object client-map test-key test-body opts)]
      (is (some? (:ETag response)) "Response should contain ETag for successful PutObject")

      (let [retrieved-obj-response (s3/get-object client-map test-key)
            retrieved-body (slurp (:Body retrieved-obj-response))]
        ;; If get-object failed, it would throw.
        (is (= test-body retrieved-body)))

      (let [tagging-response (aws/invoke tu.s3/*localstack-s3-client*
                                         {:op :GetObjectTagging
                                          :request {:Bucket tu.s3/*test-bucket-name* :Key test-key}})]
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
      (is (some? (:ETag response)) "Response should contain ETag for successful PutObject")

      (let [retrieved-obj-response (s3/get-object test-key)
            retrieved-body (slurp (:Body retrieved-obj-response))]
        ;; If get-object failed, it would throw.
        (is (= test-string retrieved-body))))))

(deftest s3-operation-failure-tests
  (testing "put-object failure"
    (let [test-key (str "failure-put-" (random-uuid) ".txt")
          test-body "This should fail to upload."
          opts {}
          faulty-aws-client (aws/client {:api :s3
                                         :endpoint-override {:protocol :http
                                                             :hostname "invalid-s3-host-that-will-fail"
                                                             :port 12345}
                                         :region (or tu.s3/test-s3-region "us-east-1")
                                         :credentials-provider (tu.s3/localstack-creds-provider)})
          faulty-s3-client-map {:client faulty-aws-client
                                :bucket-name "any-bucket"}] ; Bucket name doesn't matter if connection fails
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           (re-pattern (str "Failed to PutObject object " test-key " in bucket any-bucket"))
           (s3/put-object faulty-s3-client-map test-key test-body opts))
          "s3/put-object should throw ex-info on S3 error")
      (try
        (s3/put-object faulty-s3-client-map test-key test-body opts)
        (is false "Should have thrown")
        (catch clojure.lang.ExceptionInfo e
          (let [ex-data (ex-data e)]
            (is (= :s3/PutObject-failed (:type ex-data)))
            (is (= test-key (:key ex-data)))
            (is (= "any-bucket" (:bucket-name ex-data)))
            (is (= :PutObject (:operation ex-data)))
            (is (some? (:response ex-data))))))))

  (testing "get-object failure for non-existent key"
    (let [non-existent-key (str "non-existent-key-" (random-uuid) ".txt")]
      (is (thrown-with-msg?
           clojure.lang.ExceptionInfo
           (re-pattern (str "Failed to GetObject object " non-existent-key " in bucket " tu.s3/*test-bucket-name*))
           (s3/get-object non-existent-key)) ; Uses *s3-client* bound by fixture
          "s3/get-object should throw ex-info for non-existent key")
      (try
        (s3/get-object non-existent-key)
        (is false "Should have thrown")
        (catch clojure.lang.ExceptionInfo e
          (let [ex-data (ex-data e)]
            (is (= :s3/GetObject-failed (:type ex-data)))
            (is (= non-existent-key (:key ex-data)))
            (is (= tu.s3/*test-bucket-name* (:bucket-name ex-data)))
            (is (= :GetObject (:operation ex-data)))
            (is (some? (:response ex-data)))
          ;; For GetObject on a non-existent key, the AWS SDK typically returns
          ;; an ErrorResponse with code "NoSuchKey".
            (is (= "NoSuchKey" (get-in ex-data [:response :cognitect.aws.error/code]))
                "Expected NoSuchKey error code from S3")))))))
