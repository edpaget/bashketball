(ns app.s3
  (:require
   [aws-simple-sign.core :as aws.sign]
   [cognitect.aws.client.api :as aws]
   [integrant.core :as ig]
   [malli.experimental :as me]
   [app.registry :as registry]))

(def ^:dynamic *s3-client* nil)

(registry/defschema ::client
  [:map
   [:client :any]
   [:bucket-name :string]])

(me/defn create-client :- ::client
  "Creates an S3 client for a bucket.
  `opts` is a map of options to pass to the cognitect.aws.client.api/client function.
  It can include :region, :credentials-provider, etc.
  Example: (create-client {:region \"us-east-1\"})"
  [bucket-name :- :string
   opts :- :map]
  (prn opts)
  {:client (aws/client (merge {:api :s3} opts))
   :bucket-name bucket-name})

(defn- throw-if-error
  "Checks an AWS SDK response map for errors. If an error is found,
  throws an ex-info with details. Otherwise, returns the original response."
  [response op-type key bucket-name]
  (if (:cognitect.anomalies/category response)
    (throw (ex-info (str "Failed to " (name op-type) " object " key " in bucket " bucket-name)
                    {:type (keyword "s3" (str (name op-type) "-failed"))
                     :key key
                     :bucket-name bucket-name
                     :operation op-type
                     :response response}))
    response))

(defn put-object
  "Puts an object into an S3 bucket.
  `s3-client-map` is an optional map containing :client and :bucket-name.
  If not provided, *s3-client* dynamic var is used.
  `key` is the S3 object key.
  `body` is the content to upload (e.g., string, InputStream)."
  ([key body opts]
   (put-object *s3-client* key body opts))
  ([{:keys [client bucket-name]} key body opts]
   (throw-if-error (aws/invoke client {:op :PutObject
                                       :request (merge {:Bucket bucket-name
                                                        :Key key
                                                        :Body body}
                                                       opts)})
                   :PutObject key bucket-name)))

(defn get-object
  "Gets an object from an S3 bucket.
  `s3-client-map` is an optional map containing :client and :bucket-name.
  If not provided, *s3-client* dynamic var is used.
  `key` is the S3 object key.
  Returns the response map from aws/invoke, the object's body is an InputStream under :Body."
  ([key]
   (get-object *s3-client* key))
  ([{:keys [client bucket-name]} key]
   (throw-if-error (aws/invoke client {:op :GetObject
                                       :request {:Bucket bucket-name
                                                 :Key key}})
                   :GetObject key bucket-name)))

(defn signed-get-url
  "Generates a pre-signed URL the client can use to get an object from the s3 bucket"
  ([key]
   (signed-get-url *s3-client* key))
  ([{:keys [client bucket-name]} key]
   (aws.sign/generate-presigned-url client bucket-name key {:path-style true})))

(defmethod ig/init-key ::client [_ {:keys [config]}]
  (create-client (get-in config [:s3 :bucket-name]) (:aws-opts config)))
