(ns e2e.runner
  (:require
   [eftest.runner :as eftest]))

(defn find-and-run-tests
  "Finds and runs tests in the specified directory.
  Takes a map with a :test-dir key, e.g., {:test-dir \"test_e2e\"}."
  [{:keys [test-dir] :as opts}]
  (println (str "Running E2E tests in: " test-dir " (sequentially)"))
  (let [test-results (eftest/run-tests (eftest/find-tests test-dir) (merge {:multithread? false :include :e2e} opts))]
    (if (or (pos? (:fail test-results)) (pos? (:error test-results)))
      (System/exit 1)
      (System/exit 0))))
