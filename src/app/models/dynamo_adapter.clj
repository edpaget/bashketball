(ns app.models.dynamo-adapter
  (:require [clojure.string :as str]
            [malli.core :as m]
            [malli.transform :as mt]
            [camel-snake-kebab.core :as csk]
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

(defn- expression-attribute
  ([attribute-key]
   (expression-attribute nil attribute-key))
  ([prefix attribute-key]
   (str (when prefix ":") (csk/->snake_case (name attribute-key)))))

(defn- clojure-key
  [expression-attribute-key]
  (csk/->kebab-case-keyword expression-attribute-key))

(defn update-expression-builder
  [schema]
  (let [if-not-exist (m/walk schema gather-if-not-exist)]
    (fn [map-value]
      {:UpdateExpression
       (->> map-value
            (map (fn [[k _]]
                   (str (expression-attribute k)
                        (if (contains? if-not-exist k)
                          (str " = if_not_exists(" (expression-attribute k) ", " (expression-attribute :prefix k) ")")
                          (str " = " (expression-attribute :prefix k))))))
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
    :default-fn (fn [& _] (t/instant))
    ::mt/add-optional-keys true}))

(defn dynamo-transfomer
  []
  (mt/transformer
   (mt/key-transformer
    {:decode clojure-key
     :encode (partial expression-attribute :prefix)})
   (mt/transformer
    {:decoders
     {:map {:compile unset-keys}
      :uuid (fn [x] (parse-uuid (get x :S)))
      :string (fn [x] (get x :S))
      :int (fn [x] (Integer. (get x :N)))
      :double (fn [x] (Double. (get x :N)))
      :boolean (fn [x] (boolean (get x :BOOL)))
      :time/instant (fn [x] (when-let [timestamp (get x :S)]
                              (when-not (empty? timestamp)
                                (t/instant timestamp))))}
     :encoders
     {:uuid (fn [x] {:S (str x)})
      :string (fn [x] {:S x})
      :int (fn [x] {:N (str x)})
      :double (fn [x] {:N (str x)})
      :boolean (fn [x] {:BOOL x})
      :time/instant (fn [x] {:S (str x)})}})))
