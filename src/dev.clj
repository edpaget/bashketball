(ns dev
  (:require
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [hashp.preload]
   #_{:clj-kondo/ignore [:unused-referred-var]}
   [integrant.repl :refer [clear go halt prep init reset reset-all]]
   [shadow.cljs.devtools.api :as shadow]
   [app.db :as db]
   [dev.seed-data :as sd]))

(derive ::fe-app :duct.compiler.cljs.shadow/server)

(def config (-> (io/resource "dev.edn") slurp ig/read-string))

(ig/load-namespaces config)

(integrant.repl/set-prep! #(ig/expand config (ig/deprofile [:dev])))

(defn cljs-repl
  []
  (shadow/nrepl-select ::fe-app))

(def dev-db-pool (atom nil))

(defmethod ig/init-key ::bound-pool [_ {:keys [db-pool]}]
  (reset! dev-db-pool db-pool))

(defn seed-data!
  []
  (binding [db/*datasource* @dev-db-pool]
    (sd/seed-all!)))
