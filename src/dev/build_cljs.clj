(ns dev.build-cljs
  (:require
   [integrant.core :as ig]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]))

(def ^:private release-config (-> (io/resource "cljs-build.edn") slurp ig/read-string))
(def ^:private test-config (-> (io/resource "cljs-test-build.edn") slurp ig/read-string))

(defn -main [& args]
  (log/info "Building cljs")
  (let [command (first args)
        config (case command
                 "release" release-config
                 "test" test-config)]
    (ig/load-namespaces config)
    (ig/init config)))
