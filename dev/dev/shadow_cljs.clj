(ns dev.shadow-cljs
  "From https://github.com/duct-framework/compiler.cljs.shadow/blob/master/src/duct/compiler/cljs/shadow.clj

  Vendoring and extending to have optional hot reload and figure out repl issues."
  (:require
   [integrant.core :as ig]
   [shadow.cljs.devtools.api :as api]
   [shadow.cljs.devtools.config :as config]
   [shadow.cljs.devtools.server :as server]
   [shadow.cljs.devtools.server.common :as common]
   [shadow.cljs.devtools.server.runtime :as runtime]
   [shadow.runtime.services :as rt]))

(defn- make-runtime [config]
  (-> {::api/started (System/currentTimeMillis), :config config}
      (rt/init (common/get-system-config config))
      (rt/start-all)))

(defmacro ^:private with-runtime [runtime & body]
  `(let [body-fn# (fn [] ~@body)]
     (if (runtime/get-instance)
       (body-fn#)
       (let [runtime# ~runtime]
         (try
           (runtime/set-instance! runtime#)
           (body-fn#)
           (finally
             (runtime/reset-instance!)
             (rt/stop-all runtime#)))))))

(defn- normalize-config [key config]
  (-> (merge config/default-config config config)
      (dissoc :build)
      (assoc-in [:builds key] (:build config))
      (config/normalize)))

(defmethod ig/init-key ::release [key config]
  (let [config (normalize-config key config)]
    (with-runtime (make-runtime config)
      (api/release* (-> config :builds (get key)) {}))
    key))

(defn- quieten-loggers [loggers]
  (doseq [logger loggers]
    (doto (java.util.logging.Logger/getLogger logger)
      (.setLevel java.util.logging.Level/WARNING))))

(defmethod ig/init-key ::server [key config]
  (let [config (normalize-config key config)]
    ;; Undertow has a lot of noisy loggers so we'll change the default log
    ;; level to quieten them. Unfortunately the loggers seem to be global, so
    ;; this will affect other Undertow instances if any exist.
    (quieten-loggers ["io.undertow" "org.jboss.threads" "org.xnio"])
    (server/start! config)
    (api/watch* (-> config :builds (get key)) {:autobuild true})
    key))

(defmethod ig/halt-key! ::server [_ build-id]
  (api/stop-worker build-id)
  (server/stop!))

(defmethod ig/suspend-key! ::server [_ _])

(defmethod ig/resume-key ::server [key new-config old-config build-id]
  (if (= new-config old-config)
    (doto build-id api/watch-compile!)
    (do (ig/halt-key! key build-id)
        (ig/init-key key new-config))))
