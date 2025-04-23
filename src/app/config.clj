(ns app.config
  (:require [aero.core :as aero]
            [clojure.java.io :as io]
            [clojure.tools.logging :as log]))

(defn config
  "Loads the application configuration from resources/app-config.edn.
   Accepts an optional options map, which can include a :profile key
   to specify the Aero profile to load (e.g., :dev, :prod)."
  ([] (config {}))
  ([opts]
   (log/info "Loading configuration with options:" opts)
   (try
     (aero/read-config (io/resource "app-config.edn") opts)
     (catch Exception e
       (log/error e "Failed to load configuration from app-config.edn")
       (throw e)))))
