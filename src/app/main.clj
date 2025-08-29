(ns app.main
  "Entrypoint point for the uberjar version"
  (:require
   [app.db :as db]
   [app.db.migrate :as db.migrate]
   [app.integrant :as i]
   [clojure.tools.logging :as log])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn- usage
  "Prints usage information."
  []
  (println "Usage: java -jar app.jar <command> [<subcommand>]")
  (println)
  (println "Commands:")
  (println "  server start       (default) Starts the web server.")
  (println "  db migrate         Runs database migrations.")
  (println "  db rollback        Rolls back the last migration.")
  (println "  help               Shows this usage information.")
  (println))

(defmulti handle-cmd (fn [cmd sub _args] [cmd sub]))

(derive ::migrate ::subcommand)
(derive ::rollback ::subcommadn)
(derive ::start ::subcommand)

(defmethod handle-cmd [::server ::subcommand] [_ _ _]
  (i/with-system [{:keys [app.server/jetty]} "prod.edn"]
    (.join ^org.eclipse.jetty.server.Server jetty)))

(defmethod handle-cmd [::db ::subcommand] [_ subcommand _]
  (i/with-system [{:keys [app.db/pool]} "migrate.edn"]
    (binding [db/*datasource* pool]
      (case subcommand
        ::migrate (db.migrate/migrate)
        ::rollback (db.migrate/rollback)
        (log/info "Unknown command. Use 'migrate' or 'rollback'.")))))

(defmethod handle-cmd [::help ::subcommand] [_ _ _]
  (do (usage) (System/exit 0)))

(defmethod handle-cmd [:default ::subcommand] [_ _ _]
  (do (usage) (System/exit 1)))

(defn -main [& args]
  (let [[command subcommand] args
        command (keyword "app.main" (or command "server"))
        subcommand (or (some->> subcommand
                                (keyword "app.main"))
                       ::subcommand)]
    (handle-cmd command subcommand (rest (rest args)))))
