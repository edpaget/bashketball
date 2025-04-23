(ns dev
  (:require
   [clojure.java.io :as io]
   [integrant.core :as ig]
   #_{:clj-kondo/ignore [:unused-referred-var]}
   [integrant.repl :refer [clear go halt prep init reset reset-all]]))

(def config (-> (io/resource "dev.edn") slurp ig/read-string))

(ig/load-namespaces config)

(integrant.repl/set-prep! #(ig/expand config (ig/deprofile [:dev])))
