(ns app.models.core
  (:require
   #?(:clj
      [app.dynamo :as ddb])
   #?(:clj
      [app.models.dynamo-adapter :as ddb.adapter])
   [app.models.graphql-schema-adapter :as gql.adapter]
   [malli.core :as mc]))

(def ^:private -validator
  (memoize (fn [schema-ref]
             (mc/validator (schema schema-ref)))))

(defn validate
  [model-type model]
  ((-validator model-type) model))

#?(:clj
   (defn save-model!
     [client model-type model]
     (let [model-with-defaults (mc/decode model-type model
                                          ddb.adapter/default-now-transformer)]
       (if (validate model-type model-with-defaults)
         (do
           (ddb/update-item client (apply merge (ddb.adapter/build-update
                                                 (schema model-type)
                                                 model-with-defaults)))
           model-with-defaults)
         (throw (ex-info "Failed to validate model"
                         {:model-type model-type
                          :msg (mc/explain (schema model-type) model-with-defaults)}))))))

#?(:clj
   (defn get-model
     [client model-type partial-model]
     (let [encoded-keys (ddb.adapter/build-key (schema model-type) partial-model)
           {:keys [Item]} (ddb/get-item client encoded-keys)]
       (when Item
         (ddb.adapter/decode-dynamo (schema model-type) Item)))))

(def ^:private -graphql-encoder
  (memoize (fn [schema-ref]
             (mc/encoder (schema schema-ref) gql.adapter/transformer))))

(defn encode-graphql
  [model-type model]
  ((-graphql-encoder model-type) model))

(def ^:private -graphql-decoder
  (memoize (fn [schema-ref]
             (mc/decoder (schema schema-ref) gql.adapter/transformer))))

(defn decode-graphql
  [model-type model]
  ((-graphql-decoder model-type) model))
