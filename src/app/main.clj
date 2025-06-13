(ns app.main
  "Entrypoint point for the uberjar version"
  (:require
   [integrant.core :as ig]
   [clojure.java.io :as io])
  (:gen-class))

(set! *warn-on-reflection* true)

(defn -main [& _args]
  (let [config (-> (io/resource "prod.edn") slurp ig/read-string)]
    (ig/load-namespaces config)
    (let [{:keys [app.server/jetty]} (->> (ig/deprofile [:prod])
                                          (ig/expand config)
                                          ig/init)]
      (.join jetty))))
