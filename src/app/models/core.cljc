(ns app.models.core
  (:require [app.dynamo :as ddb]
            [app.models.dynamo-adapter :as ddb.adapter]
            [malli.core :as mc]

            [app.models.user]
            [app.models.card]
            [app.models.session]
            [malli.transform :as mt]))

(defn schema
  [schema-ref]
  (mc/deref-recursive schema-ref))

(def ^:private -dynamo-encoder
  (memoize (fn [schema-ref]
             (mc/encoder (schema schema-ref) ddb.adapter/dynamo-transfomer))))

(defn- encode
  [model-type model]
  ((-dynamo-encoder model-type) model))

(def ^:private -dynamo-decoder
  (memoize (fn [schema-ref]
             (mc/coercer (schema schema-ref) ddb.adapter/dynamo-transfomer))))

(defn- decode
  [model-type model]
  ((-dynamo-decoder model-type) model))

(def ^:private -validator
  (memoize (fn [schema-ref]
             (mc/validator (schema schema-ref)))))

(defn- validate
  [model-type model]
  ((-validator model-type) model))

(def ^:private -build-key
  (memoize (fn [schema-ref]
             (ddb.adapter/key-builder (schema schema-ref)))))

(defn- build-key
  [model-type model]
  ((-build-key model-type) model))

(def ^:private -build-update-expression
  (memoize (fn [schema-ref]
             (ddb.adapter/update-expression-builder (schema schema-ref)))))

(defn- build-update-expression
  [model-type model]
  ((-build-update-expression model-type) model))

(defn- encode-update
  [model-type model]
  {:ExpressionAttributeValues (encode model-type model)})

(def ^:private build-update (juxt build-key build-update-expression encode-update))

(defn save-model!
  [client model-type model]
  (let [model-with-defaults #p (mc/decode model-type model ddb.adapter/default-now-transformer)]
    (if (validate model-type model-with-defaults)
      (do
        (ddb/update-item client (apply merge (build-update model-type model-with-defaults)))
        model-with-defaults)
      (throw (ex-info "Failed to validate model"
                      {:model-type model-type
                       :msg (mc/explain model-type model-with-defaults)})))))

(defn get-model
  [client model-type partial-model]
  (let [encoded-keys (build-key model-type partial-model)
        {:keys [Item]} (ddb/get-item client encoded-keys)]
    (when Item
      (decode model-type Item))))
