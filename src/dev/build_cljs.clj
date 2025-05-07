(ns dev.build-cljs
  (:require
   [integrant.core :as ig]
   [clojure.java.io :as io]
   [clojure.tools.logging :as log]))

(def ^:private config (-> (io/resource "cljs-build.edn") slurp ig/read-string))

(defn -main [& _args]
  (log/info "Building cljs")
  (ig/load-namespaces config)
  (ig/init config))
