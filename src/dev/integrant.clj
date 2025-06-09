(ns dev.integrant
  "Shared vars for dev helpers and scripts to load the current system"
  (:require
   [integrant.core :as ig]
   [clojure.java.io :as io]))

(derive ::fe-app :duct.compiler.cljs.shadow/server)

(defonce dev-db-pool (atom nil))
(defonce dev-config (atom nil))

(defmethod ig/init-key ::bound-pool [_ {:keys [db-pool]}]
  (reset! dev-db-pool db-pool))

(defmethod ig/init-key ::bound-config [_ {:keys [config]}]
  (reset! dev-config config))

(defn prep
  [& [system]]
  (let [config (-> (io/resource (or system  "dev.edn")) slurp ig/read-string)]
    (ig/load-namespaces config)
    (ig/expand config (ig/deprofile [:dev]))))

(defmacro with-system
  "Starts the system with ig/init and binds resources from the system map to bindings to execute in body"
  [[bindings & [system-edn-file]] & body]
  `(let [system# (ig/init (prep ~system-edn-file))
         ~bindings system#]
     (try
       ~@body
       (finally
         (ig/halt! system#)))))
