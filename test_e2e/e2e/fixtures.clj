(ns e2e.fixtures
  (:require
   [clojure.test :refer [compose-fixtures]]
   [app.test-utils :as test-utils]
   [etaoin.api :as e]
   [clj-test-containers.core :as tc]
   [clojure.java.io :as io]
   [integrant.core :as ig]
   [clojure.tools.logging :as log])
  (:import
   [org.testcontainers Testcontainers]))

(derive ::fe-app :duct.compiler.cljs.shadow/server)

(def config (-> (io/resource "test.edn") slurp ig/read-string))

(ig/load-namespaces config)

;; Define a Testcontainer for a headless Chrome browser
(def chrome-container (atom
                       (-> (tc/create {:image-name    "selenium/standalone-chromium:latest"
                                       :exposed-ports [4444 7900]})
                           (update :container #(doto %
                                                 (.setShmSize 2147483648)
                                                 (.withAccessToHost true))))))

(def ^:dynamic *driver* nil)

;; Fixture to manage the WebDriver lifecycle
(defn webdriver-fixture [f]
  (let [container @chrome-container
        driver (e/chrome {:host (:host container)
                                   :port (get-in container [:mapped-ports 4444])
                                   :args ["--no-sandbox"]})]
    (log/infof "VNC viewer available at %s" (get-in container [:mapped-ports 7900]))
    (try
      (binding [*driver* driver] ; Bind driver for Etaoin functions
        (f))
      (finally
        (e/quit driver)))))

;; Fixture to manage the container lifecycle for all tests in this namespace
(defn container-fixture [f]
  (swap! chrome-container tc/start!)
  (try
    (f)
    (finally
      (swap! chrome-container tc/stop!))))

(defn- -server-fixture [f]
  (let [system (ig/init config)]
    (Testcontainers/exposeHostPorts (int-array [9000]))
    (try
      (f)
      (finally
        (ig/halt! system)))))

(def server-fixture (compose-fixtures -server-fixture test-utils/db-fixture))
