(ns app.models.dynamo-adapter
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.transform :as mt]
            [tick.core :as t]))

(defn key-builder
  [schema]
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
      (fn [map-value]
        (cond-> {}
          pk-cols (assoc-in [:Key "pk" :S] (key-col-builder pk-cols map-value))
          sk-cols (assoc-in [:Key "sk" :S] (key-col-builder sk-cols map-value))
          true (update-in [:Key "sk" :S] set-type))))))

(defn gather-if-not-exist
  [schema _ children _]
  (condp = (m/type schema)
    :map (->> children
              (filter (comp :dynamo/on-create second))
              (map first)
              (into #{}))
    nil))

(defn update-expression-builder
  [schema]
  (let [if-not-exist (m/walk schema gather-if-not-exist)]
    (fn [map-value]
      {:UpdateExpression
       (->> map-value
            (map (fn [[k _]]
                   (str (name k)
                        (if (contains? if-not-exist k)
                          (str " = if_not_exists(" (name k) ", :" (name k) ")")
                          (str " = :" (name k))))))
            (str/join ", ")
            (str "SET "))})))

(defn- unset-keys [schema _]
  (let [has-dynamo-keys (boolean (or (-> schema m/properties :pk)
                                     (-> schema m/properties :sk)))]
    (fn [x] (cond-> x
              has-dynamo-keys (dissoc :pk :sk)))))

(def default-now-transformer
  (mt/default-value-transformer
   {:key :default-now
    :default-fn (fn [& args] (t/instant))}))

(defn dynamo-transfomer
  []
  (mt/transformer
   (mt/key-transformer
    {:decode keyword
     :encode (fn [x] (str ":" (name x)))})
   (mt/transformer
    {:decoders
     {:map {:compile unset-keys}
      :uuid (fn [x] (parse-uuid (get x :S)))
      :string (fn [x] (get x :S))
      :int (fn [x] (Integer. (get x :N)))
      :double (fn [x] (Double. (get x :N)))
      :boolean (fn [x] (boolean (get x :BOOL)))
      :time/zoned-date-time (fn [x] (t/instant (get x :S)))}
     :encoders
     {:uuid (fn [x] {:S (str x)})
      :string (fn [x] {:S x})
      :int (fn [x] {:N (str x)})
      :double (fn [x] {:N (str x)})
      :boolean (fn [x] {:BOOL x})
      :time/zoned-date-time (fn [x] {:S (str x)})}})))
