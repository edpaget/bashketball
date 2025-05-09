(ns test.runner
  (:require
   [shadow.test.node]))

(defn -main [& args]
  (shadow.test.node/init)
  (shadow.test.node/run))

;; To make the test runner executable by node directly after compilation
(set! *main-cli-fn* -main)
