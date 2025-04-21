(ns dev.migrate
  (:require [ragtime.jdbc :as jdbc]
            [ragtime.repl :as repl]))

;; --- Ragtime Configuration ---
;; NOTE: Replace these placeholders with your actual database connection details.
;; This assumes you have a way to get your database connection spec/datasource.
;; If you use Integrant, you might fetch this from your system map.
;; Ensure the necessary JDBC driver (e.g., org.postgresql/postgresql) is on the classpath.

;; Example using a db-spec map (requires next.jdbc and the driver)
(def db-spec
  {:dbtype   "postgresql" ; Or "mysql", "h2", etc.
   :dbname   "blood_basket"
   :host     "localhost"
   :user     "postgres"
   :password "password"
   :port     5432})

(def ragtime-config
  {:datastore  (jdbc/sql-database db-spec) ; Or use (jdbc/sql-database datasource)
   :migrations (jdbc/load-resources "migrations")}) ; Looks in resources/migrations

;; --- Migration Functions ---

(defn migrate
  "Applies all pending migrations."
  []
  (println "Applying migrations...")
  (repl/migrate ragtime-config)
  (println "Migrations applied."))

(defn rollback
  "Rolls back the last applied migration."
  []
  (println "Rolling back last migration...")
  (repl/rollback ragtime-config)
  (println "Rollback complete."))

(defn -main [& args]
  (let [command (first args)]
    (cond
      (= command "migrate") (migrate)
      (= command "rollback") (rollback)
      :else (println "Unknown command. Use 'migrate' or 'rollback'."))))

