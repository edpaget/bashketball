(ns runner
  (:require
   [eftest.runner :as eftest]))

(defn find-and-run-tests
  "Entrypoint for running `clojure -X:test`"
  [args]
  (eftest/run-tests (eftest/find-tests "test") args))
