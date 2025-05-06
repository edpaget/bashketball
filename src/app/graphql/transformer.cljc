(ns app.graphql.transformer
  (:require
   [app.models]
   [camel-snake-kebab.core :as csk]
   [malli.core :as mc]
   [malli.transform :as mt]))

(def ^:private transformer
  (mt/key-transformer
   {:decode csk/->kebab-case-keyword
    :encode csk/->camelCaseKeyword}))

(defn decode
  [model encoded]
  (mc/decode model encoded transformer))
