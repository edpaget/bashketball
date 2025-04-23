(ns dev
  (:require
   [integrant.core :as ig]
   #_{:clj-kondo/ignore [:unused-referred-var]}
   [integrant.repl :refer [clear go halt prep init reset reset-all]]))

(def config
  (ig/read-string (slurp "dev.edn")))

(integrant.repl/set-prep! #(ig/expand (ig/load-namespaces config)
                                      (ig/deprofile [:dev])))
