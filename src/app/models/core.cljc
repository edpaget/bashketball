(ns app.models.core
  (:require [app.dynamo :as ddb]
            [app.models.dynamo-adapter :as da]
            [malli.core :as mc]))

(defmulti schema identity)

(defn save-model!
  [client model-type model]
  (let [model-schema (schema model-type)
        encoded (mc/encode model-schema model da/dynamo-transfomer)]
    (when (mc/validate model-schema model)
      (ddb/put-item client encoded)
      model)))

(defn get-model
  [client model-type partial-model]
  (let [model-schema (schema model-type)
        encoded-keys (select-keys (mc/encode model-schema partial-model da/dynamo-transfomer)
                                  ["pk" "sk"])
        {:keys [Item]} (ddb/get-item client encoded-keys)]
    (when Item
      (mc/coerce model-schema Item da/dynamo-transfomer))))
