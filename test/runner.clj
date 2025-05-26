(ns runner
  (:require
   [eftest.runner :as eftest]))

(defn find-and-run-tests
  "Entrypoint for running `clojure -X:test`"
  [args]
  (let [test-results (eftest/run-tests (eftest/find-tests "test") args)]
    (if (or (pos? (:fail test-results)) (pos? (:error test-results)))
      (System/exit 1)
      (System/exit 0))))
