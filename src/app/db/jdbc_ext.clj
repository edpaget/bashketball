(ns app.db.jdbc-ext
  (:require
   [jsonista.core :as json]
   [next.jdbc.result-set :as rs]
   [next.jdbc.prepare :as prepare]
   [camel-snake-kebab.core :as csk])
  (:import
   [java.sql PreparedStatement]
   [org.postgresql.util PGobject]))

(set! *warn-on-reflection* true)

(def ^:private mapper (json/object-mapper {:decode-key-fn keyword}))
(def ^:private ->json json/write-value-as-string)
(def ^:private <-json #(json/read-value % mapper))

(defn- ->pgobject
  "Transforms Clojure data to a PGobject that contains the data as
  JSON. PGObject type defaults to `jsonb` but can be changed via
  metadata key `:pgtype`"
  [x]
  (let [pgtype (or (:pgtype (meta x)) "jsonb")]
    (doto (PGobject.)
      (.setType pgtype)
      (.setValue (->json x)))))

(defn- <-pgobject
  "Transform PGobject containing `json` or `jsonb` value to Clojure data."
  [^PGobject v]
  (let [type  (.getType v)
        value (.getValue v)]
    (if (#{"jsonb" "json"} type)
      (some-> value <-json (with-meta {:pgtype type}))
      value)))

(def ^:private schema-enums #{"identity_strategy" "card_type_enum" "size_enum" "game_asset_status_enum"})

(extend-protocol rs/ReadableColumn
  java.lang.String
  (read-column-by-label [^java.lang.String v _]
    v)
  (read-column-by-index [^java.lang.String v ^java.sql.ResultSetMetaData rsmeta ^Long idx]
    (let [type (.getColumnTypeName rsmeta idx)]
      (if (contains? schema-enums type)
        (keyword (csk/->kebab-case type) v)
        v))))

;; if a SQL parameter is a Clojure hash map or vector, it'll be transformed
;; to a PGobject for JSON/JSONB:
(extend-protocol prepare/SettableParameter
  clojure.lang.IPersistentMap
  (set-parameter [m ^PreparedStatement s i]
    (.setObject s i (->pgobject m)))

  clojure.lang.IPersistentVector
  (set-parameter [v ^PreparedStatement s i]
    (.setObject s i (->pgobject v))))

;; if a row contains a PGobject then we'll convert them to Clojure data
;; while reading (if column is either "json" or "jsonb" type):
(extend-protocol rs/ReadableColumn
  org.postgresql.util.PGobject
  (read-column-by-label [^org.postgresql.util.PGobject v _]
    (<-pgobject v))
  (read-column-by-index [^org.postgresql.util.PGobject v _2 _3]
    (<-pgobject v)))
