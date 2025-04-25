(ns app.test-utils
  (:require
   [app.config :as config]
   [app.db :as db]
   [app.db.connection-pool :as db.pool]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [next.jdbc :as jdbc]
   [next.jdbc.transaction]
   [ragtime.next-jdbc :as ragtime.next-jdbc]
   [ragtime.repl :as ragtime.repl]))

(set! *warn-on-reflection* true)

;; --- Test Configuration ---
(def test-config (config/config {:profile :test}))

;; --- Ragtime Configuration ---
(defn- ragtime-config [datasource]
  {:datastore  (ragtime.next-jdbc/sql-database datasource)
   :migrations (ragtime.next-jdbc/load-resources "migrations")})

;; --- Test Fixtures ---

(defn db-fixture [f]
  (let [db-url (:database-url test-config)]
    (log/info "Ensuring test database exists for URL:" db-url)
    (try
      (let [uri (java.net.URI. (str/replace-first db-url #"^jdbc:" ""))
            scheme (.getScheme uri)
            host (.getHost uri)
            port (let [p (.getPort uri)] (if (pos? p) p 5432)) ; Default PG port 5432
            path (.getPath uri)
            db-name (when (and path (> (count path) 1)) (subs path 1)) ; Remove leading '/'
            query-params (.getQuery uri)
            ;; Construct server URL (connect to 'postgres' db)
            server-base (str "jdbc:" scheme "://"  host ":" port "/")
            server-url (str server-base "postgres" (when query-params (str "?" query-params)))]

        (when-not db-name
          (throw (ex-info "Could not parse database name from JDBC URL" {:url db-url})))

        (log/info "Connecting to" server-url "to check/create database" db-name)
        (with-open [conn (jdbc/get-connection server-url)]
          (try
            ;; Use quoting for the database name in case it needs it
            (let [sql (format "CREATE DATABASE \"%s\"" db-name)]
              (log/info "Executing:" sql)
              (jdbc/execute! conn [sql]))
            (log/info "Database" db-name "created successfully.")
            (catch java.sql.SQLException e
              ;; Check if the error is "database already exists" (PostgreSQL specific SQLState 42P04)
              (if (= "42P04" (.getSQLState e))
                (log/info "Database" db-name "already exists.")
                (do
                  (log/error e "SQL error during database creation check")
                  (throw e)))))         ; Rethrow other SQL errors
          (log/info "Database" db-name "is ready.")))
      (catch Exception e ; Catch parsing errors or rethrown errors from inner blocks
        (log/error e "Failed to ensure database existence for" db-url)
        ;; Throw a more informative error to the user
        (throw (ex-info (str "Failed to ensure test database exists. Check URL, permissions, server status. Original error: " (ex-message e))
                        {:url db-url} e))))
    (log/info "Creating connection pool for" db-url)
    (let [ds (db.pool/create-pool db-url)] ; Create pool using the original URL
      (binding [db/*datasource* ds]     ; Bind dynamic var for tests using it
        (log/info "Running migrations...")
        (ragtime.repl/migrate (ragtime-config ds)) ; Apply migrations
        (try
          (f)                           ; Run tests
          (finally
            (log/info "Rolling back migrations...")
            (ragtime.repl/rollback (ragtime-config ds) Integer/MAX_VALUE)
            (db.pool/close-pool! ds)))))))

(defn rollback-fixture [f]
  (if-let [ds db/*datasource*] ; Relies on db/*datasource* being bound by db-fixture
    (jdbc/with-transaction [tx ds {:rollback-only true}]
      (binding [db/*current-connection* tx                 ; Bind connection for tests using it
                next.jdbc.transaction/*nested-tx* :ignore] ; Set nested transaction to ignore to allow silently nested transactions
        (f)))
    (throw (IllegalStateException. "Test datasource is not initialized for transaction fixture. Ensure db-fixture runs first."))))
