(ns app.db
  (:require
   [app.db.connection-pool :as db.pool]
   [integrant.core :as ig]
   [next.jdbc :as jdbc]
   [honey.sql :as sql]))

(def ^:dynamic *datasource*
  "Dynamic var holding the application's datasource (connection pool).
   Must be bound before calling query functions without an explicit connectable."
  nil)

(def ^:dynamic *current-connection*
  "Dynamic var holding the current active JDBC connection, if any.
   Used by `with-connection` and query functions to manage transactions
   or reuse a connection."
  nil)

(defn- compile-honeysql
  "Compiles a HoneySQL map into a JDBC-compatible vector [sql-string params...].
   If the input is not a map, returns it unchanged."
  [sql-map-or-vec]
  (if (map? sql-map-or-vec)
    (sql/format sql-map-or-vec)
    sql-map-or-vec))

(defn do-with-connection
  "Internal helper. Executes function `f` within a connection context.
   - If *current-connection* is bound and non-nil, executes `f` directly.
   - Otherwise, if *datasource* is bound and non-nil, obtains a connection,
     binds it to *current-connection*, executes `f`, and closes connection.
   - Throws if neither is available."
  [f]
  (cond
    *current-connection*
    ;; pass the current connection to the f explicitly to allow it to be used
    ;; with other JBDC interfaces
    (f *current-connection*) ; Use existing connection
    *datasource*
    (with-open [conn (jdbc/get-connection *datasource*)]
      (binding [*current-connection* conn] ; Bind connection for f
        ;; pass the current connection to the f explicitly to allow it to be used
        ;; with other JBDC interfaces
        (f *current-connection*))) ; Execute with new connection
    :else
    (throw (IllegalStateException. "No active connection (*current-connection*) or datasource (*datasource*) available."))))

(defn- call-jdbc-fn
  "Helper to call the appropriate next.jdbc function, managing connections.
   `jdbc-fn` is the function like jdbc/execute!
   `connectable-or-sql` is the first arg passed to our wrapper fn.
   `sql-or-opts` is the second arg passed (SQL or options map).
   `opts` is the third arg (optional options map)."
  [jdbc-fn connectable-or-sql sql-or-opts & [opts]]
  (if (instance? java.sql.Connection connectable-or-sql)
    (let [sql-vec (compile-honeysql sql-or-opts)]
      (if opts
        (jdbc-fn connectable-or-sql sql-vec opts)
        (jdbc-fn connectable-or-sql sql-vec)))
    ;; No explicit connectable, rely on dynamic vars
    (let [sql-vec (compile-honeysql connectable-or-sql) ; First arg is SQL/HoneySQL
          effective-opts (if-not (nil? sql-or-opts) sql-or-opts opts)] ; Second arg might be opts
      (if (and (bound? #'*current-connection*) *current-connection*)
        ;; Use existing bound connection
        (if effective-opts
          (jdbc-fn *current-connection* sql-vec effective-opts)
          (jdbc-fn *current-connection* sql-vec))
        ;; No existing connection, use do-with-connection to get one
        (do-with-connection
         (fn [_]                  ; Function to run within the connection context
           (if effective-opts
             (jdbc-fn *current-connection* sql-vec effective-opts) ; *current-connection* is now bound
             (jdbc-fn *current-connection* sql-vec))))))))

;; Define the public query functions

(defn plan
  "Executes a query that returns a reducible collection of rows.
   If the first argument `connectable-or-sql` is a connectable (datasource or connection),
   uses it. Otherwise, obtains a connection from the dynamic var *datasource*.
   The SQL argument (`sql-or-opts` or `connectable-or-sql`) can be a HoneySQL map
   or a standard [sql params...] vector.
   Accepts optional `opts` map as per next.jdbc/plan."
  ([sql]
   (call-jdbc-fn jdbc/plan sql nil))
  ([connectable-or-sql sql-or-opts]
   (call-jdbc-fn jdbc/plan connectable-or-sql sql-or-opts))
  ([connectable-or-sql sql-or-opts opts]
   (call-jdbc-fn jdbc/plan connectable-or-sql sql-or-opts opts)))

(defn execute!
  "Executes SQL (e.g., DDL, INSERT, UPDATE, DELETE) and returns update counts.
   If the first argument `connectable-or-sql` is a connectable (datasource or connection),
   uses it. Otherwise, obtains a connection from the dynamic var *datasource*.
   The SQL argument (`sql-or-opts` or `connectable-or-sql`) can be a HoneySQL map
   or a standard [sql params...] vector.
   Accepts optional `opts` map as per next.jdbc/execute!."
  ([sql]
   (call-jdbc-fn jdbc/execute! sql nil))
  ([connectable-or-sql sql-or-opts]
   (call-jdbc-fn jdbc/execute! connectable-or-sql sql-or-opts))
  ([connectable-or-sql sql-or-opts opts]
   (call-jdbc-fn jdbc/execute! connectable-or-sql sql-or-opts opts)))

(defn execute-one!
  "Executes SQL (typically SELECT) that returns a single row map.
   If the first argument `connectable-or-sql` is a connectable (datasource or connection),
   uses it. Otherwise, obtains a connection from the dynamic var *datasource*.
   The SQL argument (`sql-or-opts` or `connectable-or-sql`) can be a HoneySQL map
   or a standard [sql params...] vector.
   Accepts optional `opts` map as per next.jdbc/execute-one!."
  ([sql]
   (call-jdbc-fn jdbc/execute-one! sql nil))
  ([connectable-or-sql sql-or-opts]
   (call-jdbc-fn jdbc/execute-one! connectable-or-sql sql-or-opts))
  ([connectable-or-sql sql-or-opts opts]
   (call-jdbc-fn jdbc/execute-one! connectable-or-sql sql-or-opts opts)))

(defmacro with-connection
  "Ensures database operations within the body run within a connection context.
   If *current-connection* is already bound, uses it. Otherwise, obtains a
   new connection from *datasource*, binds it to *current-connection*,
   and executes the body. The new connection is closed afterwards."
  [[conn] & body]
  `(do-with-connection (fn [~conn] ~@body)))

(defmethod ig/init-key ::pool [_ {:keys [config]}]
  (db.pool/create-pool (:database-url config) (:c3p0-opts config)))

(defmethod ig/halt-key! ::pool [_ datasource]
  (db.pool/close-pool! datasource))
