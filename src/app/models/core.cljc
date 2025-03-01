(ns app.models.core
  (:require [app.dynamo :as ddb]
            [app.models.dynamo-adapter :as da]
            [malli.core :as mc]

            [app.models.user]
            [app.models.card]))

(defn schema
  [schema-ref]
  (mc/deref-recursive schema-ref))

(def ^:private -dynamo-encoder
  (memoize (fn [schema-ref]
             (mc/encoder (schema schema-ref) da/dynamo-transfomer))))

(defn- encode
  [model-type model]
  ((-dynamo-encoder model-type) model))

(def ^:private -dynamo-decoder
  (memoize (fn [schema-ref]
             (mc/coercer (schema schema-ref) da/dynamo-transfomer))))

(defn- decode
  [model-type model]
  ((-dynamo-decoder model-type) model))

(def ^:private -validator
  (memoize (fn [schema-ref]
             (mc/validator (schema schema-ref)))))

(defn- validate
  [model-type model]
  ((-validator model-type) model))

(defn save-model!
  [client model-type model]
  (when (validate model-type model)
    (ddb/put-item client (encode model-type model))
    model))

(defn get-model
  [client model-type partial-model]
  (let [encoded-keys (select-keys (encode model-type partial-model)
                                  ["pk" "sk"])
        {:keys [Item]} (ddb/get-item client encoded-keys)]
    (when Item
      (decode model-type Item))))
