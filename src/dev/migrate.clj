(ns dev.migrate
  (:require
   [integrant.core :as ig]
   [clojure.tools.logging :as log]
   [clojure.java.io :as io]
   [ragtime.next-jdbc :as next-jdbc]
   [ragtime.repl :as repl]))

(def ^:dynamic *ragtime-config* nil)

(def ^:private config (-> (io/resource "migrate.edn") slurp ig/read-string))

(defn do-with-config
  [thunk]
  (ig/load-namespaces config)
  (let [{:keys [app.db/pool] :as system} (ig/init config)]
    (try
      (binding [*ragtime-config* {:datastore  (next-jdbc/sql-database pool)
                                  :migrations (next-jdbc/load-resources "migrations")}]
        (thunk))
      (catch Throwable e
        (log/error e "Migration failed with error"))
      (finally
        (ig/halt! system)))))

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
    (cond
      (= command "migrate") (migrate)
      (= command "rollback") (rollback)
      :else (log/info "Unknown command. Use 'migrate' or 'rollback'."))))
