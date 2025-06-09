(ns dev
  (:require
   [hashp.preload]
   #_{:clj-kondo/ignore [:unused-referred-var]}
   [integrant.repl :refer [clear go halt prep init reset reset-all]]
   [shadow.cljs.devtools.api :as shadow]
   [app.db :as db]
   [dev.integrant :as i]
   [dev.seed-data :as sd]
   [dev.migrate :as m]))

(integrant.repl/set-prep! i/prep)

(defn cljs-repl
  []
  (shadow/nrepl-select ::fe-app))

(defn seed-data!
  []
  (binding [db/*datasource* @i/dev-db-pool]
    (sd/seed-all!)))

(defn migrate
  []
  (binding [db/*datasource* @i/dev-db-pool]
    (m/migrate)))

(defn rollback
  []
  (binding [db/*datasource* @i/dev-db-pool]
    (m/rollback)))
