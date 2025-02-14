(ns user
  (:require [app.server :as server]
            [shadow.cljs.devtools.server :as shadow.server]
            [shadow.cljs.devtools.api :as shadow]
            [integrant.core :as ig]
            [integrant.repl :refer [clear go halt prep init reset reset-all]]))

(def config (assoc server/config :shadow/server {}))

(defmethod ig/init-key :shadow/server [_ _]
  (shadow.server/start!)
  (shadow/watch :app))

(defmethod ig/halt-key! :shadow/server [_ _]
  (shadow/stop-worker :app)
  (shadow.server/stop!))

(integrant.repl/set-prep! #(ig/expand config (ig/deprofile [:dev])))
