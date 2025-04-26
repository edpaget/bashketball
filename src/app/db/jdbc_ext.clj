(ns app.db.jdbc-ext
  (:require
   [next.jdbc.result-set :as rs]
   [camel-snake-kebab.core :as csk]))

(set! *warn-on-reflection* true)

(def schema-enums #{"identity_strategy"})

(extend-protocol rs/ReadableColumn
  java.lang.String
  (read-column-by-label [^java.lang.String v _]
    v)
  (read-column-by-index [^java.lang.String v ^java.sql.ResultSetMetaData rsmeta ^Long idx]
    (let [type (.getColumnTypeName rsmeta idx)]
      (if (contains? schema-enums type)
        (keyword (csk/->kebab-case type) v)
        v))))
