(ns app.db.connection-pool
  (:require [next.jdbc.connection :as connection]
            [clojure.tools.logging :as log])
  (:import [com.mchange.v2.c3p0 ComboPooledDataSource]))

(defn create-pool
  "Creates and returns a C3P0 ComboPooledDataSource connection pool.
   Accepts the full JDBC database URL as the first argument.
   The URL should contain necessary credentials if required by the database.
   Optionally accepts a map `opts` as the second argument to override
   default C3P0 settings."
  ([db-url] (create-pool db-url {}))
  ([db-url opts]
   (try
     (log/info "Creating database connection pool...")
     (let [base-spec {:jdbcUrl db-url}
              ;; Merge base spec, defaults, and user-provided opts
           db-spec (merge base-spec opts)
           pool (connection/->pool ComboPooledDataSource db-spec)]
       (log/info "Database connection pool created successfully.")
       pool)
     (catch Exception e
       (log/error e "Failed to create database connection pool")
       (throw e))))) ; Re-throw the exception after logging

(defn close-pool!
  "Closes the provided C3P0 datasource pool."
  [datasource]
  (when (instance? ComboPooledDataSource datasource)
    (log/info "Closing database connection pool...")
    (try
      (.close ^ComboPooledDataSource datasource)
      (log/info "Database connection pool closed.")
      (catch Exception e
        (log/error e "Error closing database connection pool")))))
