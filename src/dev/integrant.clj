(ns dev.integrant
  "Shared vars for helpers and scripts to load the current system"
  (:require
   [integrant.core :as ig]))

(derive ::fe-app :duct.compiler.cljs.shadow/server)

(defonce dev-db-pool (atom nil))
(defonce dev-config (atom nil))

(defmethod ig/init-key ::bound-pool [_ {:keys [db-pool]}]
  (reset! dev-db-pool db-pool))

(defmethod ig/init-key ::bound-config [_ {:keys [config]}]
  (reset! dev-config config))
