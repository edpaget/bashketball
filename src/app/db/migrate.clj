(ns app.db.migrate
  (:require
   [app.db :as db]
   [clojure.tools.logging :as log]
   [ragtime.next-jdbc :as next-jdbc]
   [ragtime.repl :as repl]))

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
  (with-config
    (repl/migrate *ragtime-config*)))

(defn rollback
  "Rolls back the last applied migration."
  []
  (with-config
    (repl/rollback *ragtime-config*)))
