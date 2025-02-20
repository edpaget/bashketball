(ns app.models.dynamo-adapter
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.transform :as mt]))

(defn- set-keys
  [schema _]
  (let [pk-cols (-> schema m/properties :pk)
        sk-cols (-> schema m/properties :sk)
        type (-> schema m/properties :type)]
    (letfn [(key-col-builder [key-cols map-data]
              (str/join "#" (->> ((apply juxt key-cols) map-data)
                                 (map str)
                                 (map vector (map name key-cols))
                                 (map #(str/join ":" %)))))
            (set-type [sk]
              (cond-> type
                (not-empty sk) (str "#" sk)))]
      (fn [x]
        (cond-> x
          pk-cols (assoc "pk" {:S (key-col-builder pk-cols x)})
          sk-cols (assoc "sk" {:S (key-col-builder sk-cols x)})
          true (update-in ["sk" :S] set-type))))))

(defn- unset-keys [schema _]
  (let [has-dynamo-keys (boolean (or (-> schema m/properties :pk)
                                     (-> schema m/properties :sk)))]
    (fn [x] (cond-> x
              has-dynamo-keys (dissoc :pk :sk)))))

(defn dynamo-transfomer
  []
  (mt/transformer
   (mt/key-transformer
    {:decode keyword
     :encode name})
   (mt/transformer
    {:decoders
     {:map {:compile unset-keys}
      :string (fn [x] (get x :S))
      :int (fn [x] (Integer. (get x :N)))
      :double (fn [x] (Double. (get x :N)))
      :boolean (fn [x] (boolean (get x :BOOL)))}
     :encoders
     {:map {:compile set-keys}
      :string (fn [x] {:S x})
      :int (fn [x] {:N (str x)})
      :double (fn [x] {:N (str x)})
      :boolean (fn [x] {:BOOL x})}})))
