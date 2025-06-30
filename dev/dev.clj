(ns dev
  (:require
   [app.db :as db]
   [app.db.migrate :as db.m]
   [app.integrant :as app.i]
   [clojure.tools.logging :as log]
   [dev.integrant :as dev.i]
   [dev.seed-data :as sd]
   [hashp.preload]
   #_{:clj-kondo/ignore [:unused-referred-var]}
   [integrant.repl :refer [clear go halt prep init reset reset-all]]
   [shadow.cljs.devtools.api :as shadow]))

(integrant.repl/set-prep! app.i/prep)

(defn cljs-repl
  "Connect to the running dev shadow-cljs"
  []
  (shadow/repl :dev.integrant/fe-app))

(defn seed-data!
  []
  (binding [db/*datasource* @dev.i/dev-db-pool]
    (sd/seed-all!)))

(defn migrate
  "Applies all pending migrations."
  []
  (log/info "Applying migrations...")
  (binding [db/*datasource* @dev.i/dev-db-pool]
    (db.m/migrate))
  (log/info "Migrations applied."))

(defn rollback
  "Rolls back the last applied migration."
  []
  (log/info "Rolling back last migration...")
  (binding [db/*datasource* @dev.i/dev-db-pool]
    (db.m/rollback))
  (log/info "Rollback complete."))
