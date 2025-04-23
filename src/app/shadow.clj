(ns app.shadow
  (:require
   [integrant.core :as ig]
   [shadow.cljs.devtools.api :as shadow]
   [shadow.cljs.devtools.server :as shadow.server]))

(defmethod ig/init-key :shadow/server [_ _]
  (shadow.server/start!)
  (shadow/watch :app))

(defmethod ig/halt-key! :shadow/server [_ _]
  (shadow/stop-worker :app)
  (shadow.server/stop!))
