(ns app.graphql.transformer
  (:require
   [malli.transform :as mt]
   [camel-snake-kebab.core :as csk]))

(def transformer
  (mt/key-transformer
   {:decode csk/->kebab-case-keyword
    :encode csk/->camelCaseKeyword}))
