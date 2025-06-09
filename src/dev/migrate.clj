(ns dev.migrate
  (:require
   [dev.integrant :as i]
   [clojure.tools.logging :as log]
   [ragtime.next-jdbc :as next-jdbc]
   [ragtime.repl :as repl]
   [app.db :as db]))

(def ^:dynamic *ragtime-config* nil)

(defn do-with-config
  [thunk]
  (try
    (binding [*ragtime-config* {:datastore  (next-jdbc/sql-database db/*datasource*)
                                :migrations (next-jdbc/load-resources "migrations")}]
      (thunk))
    (catch Throwable e
      (log/error e "Migration failed with error"))))

(defmacro with-config
  [& body]
  `(do-with-config (fn [] ~@body)))

;; --- Migration Functions ---

(defn migrate
  "Applies all pending migrations."
  []
  (log/info "Applying migrations...")
  (with-config
    (repl/migrate *ragtime-config*))
  (log/info "Migrations applied."))

(defn rollback
  "Rolls back the last applied migration."
  []
  (log/info "Rolling back last migration...")
  (with-config
    (repl/rollback *ragtime-config*))
  (log/info "Rollback complete."))

(defn -main [& args]
  (let [command (first args)]
    (i/with-system [{:keys [app.db/pool]} "migrate.edn"]
      (binding [db/*datasource* pool]
        (cond
          (= command "migrate") (migrate)
          (= command "rollback") (rollback)
          :else (log/info "Unknown command. Use 'migrate' or 'rollback'."))))))
