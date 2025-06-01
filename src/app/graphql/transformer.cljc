(ns app.graphql.transformer
  (:require
   [app.models]
   [camel-snake-kebab.core :as csk]
   [malli.core :as mc]
   [malli.transform :as mt]))

(def ^:private enum-transformer
  "A Malli transformer for enum values.
  Encodes namespaced keywords to their string name (e.g., :my-ns/FOO -> \"FOO\").
  Decodes strings back to namespaced keywords if they match an option in an [:enum ...] schema
  (e.g., \"FOO\" -> :my-ns/FOO, assuming :my-ns/FOO is a valid schema option)."
  (mt/transformer
   {;;:name :enum-transformer
    :decoders
    {:enum ; Target schemas of type :enum
     {:compile
      (fn [schema _options] ; schema is like [:enum :ns/A :ns/B]
        (let [enum-options (mc/children schema)
               ;; Infer namespace from the first keyword option.
               ;; Assumes all enum options in a schema share the same namespace if they are keywords.
              enum-namespace (when-let [first-option (first enum-options)]
                               (when (keyword? first-option)
                                 (namespace first-option)))]
          (fn [value] ; value is the string from GraphQL, e.g., "A"
            (if (and (string? value) enum-namespace)
              (let [kw-value (keyword enum-namespace value)]
                 ;; Only convert to keyword if it's a valid option in the schema.
                (if (some #(= kw-value %) enum-options)
                  kw-value
                  value)) ; Return original string if not a valid enum member; Malli will validate.
              value))))}}
    :encoders
    {:enum name}}))

(def ^:private kebab-key-transformer
  "A Malli transformer for map keys.
  Decodes keys from camelCase to kebab-case keywords.
  Encodes keys from kebab-case keywords to camelCase keywords."
  (mt/key-transformer
   {:decode csk/->kebab-case-keyword
    :encode csk/->camelCaseKeyword}))

(def ^:private decoding-transformer
  "Composite transformer for decoding GraphQL inputs.
  Order of operations:
  1. Transform map keys (camelCase string/keyword -> kebab-case keyword).
  2. Transform enum values (string -> namespaced keyword).
  3. Apply default values for missing fields."
  (mt/transformer
   kebab-key-transformer
   enum-transformer
   mt/default-value-transformer))

(def ^:private encoding-transformer
  "Composite transformer for encoding data to GraphQL outputs.
  Order of operations:
  1. Transform enum values (namespaced keyword -> string).
  2. Transform map keys (kebab-case keyword -> camelCase keyword)."
  (mt/transformer
   enum-transformer
   kebab-key-transformer))

(defn decode
  "Decodes GraphQL input data (e.g., from a mutation) according to the given Malli model.
  Applies key, enum, and default value transformations."
  [encoded model]
  (mc/decode model encoded decoding-transformer))

(defn encode
  "Encodes application data into a format suitable for GraphQL output according to the given Malli model.
  Applies enum and key transformations."
  [decoded model]
  (mc/encode model decoded encoding-transformer))
