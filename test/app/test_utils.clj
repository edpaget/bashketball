(ns app.test-utils
  (:require
   [app.authn.handler :as authn.handler]
   [app.config :as config]
   [app.db :as db]
   [app.db.connection-pool :as db.pool]
   [app.models :as models]
   [app.test-utils.initializer :refer [initialized]]
   [clojure.string :as str]
   [clojure.tools.logging :as log]
   [integrant.core :as ig]
   [java-time.api :as t]
   [next.jdbc :as jdbc]
   [next.jdbc.transaction]
   [ragtime.next-jdbc :as ragtime.next-jdbc]
   [ragtime.repl :as ragtime.repl])
  (:import
   [java.util.concurrent.locks ReentrantLock]))

(set! *warn-on-reflection* true)

;; --- Test Configuration ---
(def test-config (config/config {:profile :test}))

;; --- Ragtime Configuration ---
(defn- ragtime-config [datasource]
  {:datastore  (ragtime.next-jdbc/sql-database datasource)
   :migrations (ragtime.next-jdbc/load-resources "migrations")})

;; --- Test Fixtures ---

(defonce ^:private ^ReentrantLock db-fixture-lock (ReentrantLock.))

(defn db-fixture [f]
  (let [db-url (:database-url test-config)]
    (.lock db-fixture-lock)
    (when (= 0 (:db @initialized))
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
                    (throw e)))))     ; Rethrow other SQL errors
            (log/info "Database" db-name "is ready.")))
        (catch Exception e ; Catch parsing errors or rethrown errors from inner blocks
          (.unlock db-fixture-lock)
          (log/error e "Failed to ensure database existence for" db-url)
          ;; Throw a more informative error to the user
          (throw (ex-info (str "Failed to ensure test database exists. Check URL, permissions, server status. Original error: " (ex-message e))
                          {:url db-url} e))))
      (log/info "Creating connection pool for" db-url))
    (let [ds (db.pool/create-pool db-url)] ; Create pool using the original URL
      (when (= 0 (:db @initialized))
        (log/info "Running migrations...")
        (ragtime.repl/migrate (ragtime-config ds))) ; Apply migrations
      (swap! initialized update :db inc)
      (.unlock db-fixture-lock)
      (try
        (binding [db/*datasource* ds] ; Bind dynamic var for tests using it
          (f))                       ; Run tests
        (finally
          (try
            (.lock db-fixture-lock)
            (when (= 1 (:db @initialized))
              (log/info "Rolling back migrations...")
              (ragtime.repl/rollback (ragtime-config ds) Integer/MAX_VALUE))
            (swap! initialized update :db dec)
            (finally
              (.unlock db-fixture-lock)))
          (db.pool/close-pool! ds))))))

(defn rollback-fixture [f]
  (if-let [ds db/*datasource*] ; Relies on db/*datasource* being bound by db-fixture
    (jdbc/with-transaction [tx ds {:rollback-only true}]
      (binding [db/*current-connection* tx                 ; Bind connection for tests using it
                next.jdbc.transaction/*nested-tx* :ignore] ; Set nested transaction to ignore to allow silently nested transactions
        (f)))
    (throw (IllegalStateException. "Test datasource is not initialized for transaction fixture. Ensure db-fixture runs first."))))

;; --- Helper Macros ---

(defn do-global-frozen-time
  "Internal helper for with-global-frozen-time."
  [f]
  (if-let [tx db/*current-connection*] ; Check if inside a transaction
    (let [ts-str (t/format :iso-offset-date-time (t/with-offset (t/offset-date-time) 0))] ; Ensure UTC
      (log/debug "Freezing transaction time to:" ts-str)
      ;; Set the custom session variable used by get_current_timestamp()
      (jdbc/execute! tx [(str "SET LOCAL vars.frozen_timestamp = '" ts-str "'")])
      (f)
      ;; Unset the custom session variable
      (jdbc/execute! tx ["SET LOCAL vars.frozen_timestamp = ''"])) ; Run the test code
    (throw (IllegalStateException. "freeze-time-fixture must run within a transaction (e.g., inside rollback-fixture)"))))

(defmacro with-global-frozen-time
  "Freezes the time via java-time.api/with-clock and also freezes time within the current postgresql transaction."
  [time & body]
  `(t/with-clock (t/mock-clock ~time)
     (do-global-frozen-time
      (fn [] ~@body))))

(defn wrap-lift
  [accum key]
  (update accum key #(vector :lift %1)))

(defmacro with-inserted-data
  "Inserts data into specified tables before executing the body,
   then deletes the inserted rows afterwards. Ensures cleanup even if body throws.

   Bindings should be a vector: [model-keyword data-map ...]
   e.g., [::models/Actor {:id \"actor1\"} ::models/Identity {:provider :identity-strategy/SIGN_IN_WITH_GOOGLE :provider-identity \"actor1\"}]

   Requires an active transaction (e.g., within rollback-fixture)."
  [bindings & body]
  (when-not (vector? bindings)
    (throw (IllegalArgumentException. "Bindings must be a vector.")))
  (when-not (even? (count bindings))
    (throw (IllegalArgumentException. "Bindings must have an even number of elements (model-keyword data-map pairs).")))

  (let [pairs (partition 2 bindings)
        ;; Generate symbols for storing insert results (IDs)
        id-syms (mapv (fn [_] (gensym "inserted-id-")) pairs)
        ;; Generate let bindings for insertion
        let-bindings (mapcat (fn [[model-kw data-map] id-sym]
                               `[~id-sym (let [table# (models/->table-name ~model-kw)
                                               lift-keys# (models/->set-lift ~model-kw)
                                               data-map# (reduce wrap-lift
                                                                 ~data-map
                                                                 lift-keys#)
                                               cols# (vec (keys data-map#))
                                               vals# (vec (vals data-map#))]
                                           (prn lift-keys#)
                                           (log/debug "Inserting into" table# "with data:" ~data-map)
                                           (db/execute-one! {:insert-into table#
                                                             :columns     cols#
                                                             :values      [vals#]
                                                             :returning   (models/->pk ~model-kw)}))])
                             pairs id-syms)
        ;; Generate cleanup forms (delete in reverse order)
        cleanup-forms (map (fn [[model-kw _] id-sym]
                             `(let [table# (models/->table-name ~model-kw)
                                    pk-keys# (models/->pk ~model-kw)
                                    pk-vals# (map #(get ~id-sym %) pk-keys#)] ; Get PK values from result map
                                (when (every? some? pk-vals#) ; Only delete if insert succeeded and returned a full PK
                                  (log/debug "Deleting from" table# "where PK" pk-keys# "=" pk-vals#)
                                  (db/execute! {:delete-from table#
                                                :where       (into [:and]
                                                                   (map (fn [k# v#]
                                                                          (if (keyword? v#) ; Handle potential enums in PK
                                                                            [:= k# (db/->pg_enum v#)]
                                                                            [:= k# v#]))
                                                                        pk-keys# pk-vals#))}))))
                           (reverse pairs) (reverse id-syms))]
    `(let [~@let-bindings]
       (try
         ~@body
         (finally
           ~@cleanup-forms)))))

;; --- Mocks ---

;; Mock auth handler that does accepts a token with a constant value
(defmethod ig/init-key ::auth-handler [_ {:keys [config]}]
  (let [{:keys [cookie-name]} (:auth config)]
    (authn.handler/make-authn-handler
     {:cookie-name cookie-name
      :authorization-creator
      (authn.handler/make-token-authorization-creator
       {:authenticator (authn.handler/make-id-token-authenticator
                        {:jwks-url ""
                         :email-validator (constantly true)
                         :strategy :identity-strategy/INVALID}
                        (fn [_jwks-url token]
                          (when (= "test-user-token" token)
                            {:email "test-user@gmail.com"})))})})))
