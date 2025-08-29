(ns app.integrant
  "Shared vars for helpers and scripts to load the current system"
  (:require
   [clojure.java.io :as io]
   [integrant.core :as ig]))

(defn prep
  [& [system]]
  (let [config (-> (io/resource (or system "dev.edn")) slurp ig/read-string)]
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
