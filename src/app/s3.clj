(ns app.s3
  (:require
   [cognitect.aws.client.api :as aws]
   [integrant.core :as ig]))

(def ^:dynamic *s3-client* nil)

(defn create-client
  "Creates an S3 client for a bucket.
  `opts` is a map of options to pass to the cognitect.aws.client.api/client function.
  It can include :region, :credentials-provider, etc.
  Example: (create-client {:region \"us-east-1\"})"
  [bucket-name opts]
  {:client (aws/client (merge {:api :s3} opts))
   :bucket-name bucket-name})

(defn put-object
  "Puts an object into an S3 bucket.
  `s3-client-map` is an optional map containing :client and :bucket-name.
  If not provided, *s3-client* dynamic var is used.
  `key` is the S3 object key.
  `body` is the content to upload (e.g., string, InputStream)."
  ([key body opts]
   (put-object *s3-client* key body opts))
  ([{:keys [client bucket-name]} key body opts]
   (aws/invoke client {:op :PutObject
                       :request (merge {:Bucket bucket-name
                                        :Key key
                                        :Body body}
                                       opts)})))

(defn get-object
  "Gets an object from an S3 bucket.
  `s3-client-map` is an optional map containing :client and :bucket-name.
  If not provided, *s3-client* dynamic var is used.
  `key` is the S3 object key.
  Returns the response map from aws/invoke, the object's body is an InputStream under :Body."
  ([key]
   (get-object *s3-client* key))
  ([{:keys [client bucket-name]} key]
   (aws/invoke client {:op :GetObject
                       :request {:Bucket bucket-name
                                 :Key key}})))

(defmethod ig/init-key ::client [_ {:keys [config]}]
  (create-client (get-in config [:s3 :bucket-name]) (:aws-opts config)))
