(ns app.server
  (:require [reitit.ring :as r]
            [ring.adapter.jetty :refer [run-jetty]]
            [integrant.core :as ig])
  (:gen-class))

(defn ping-handler [_]
  {:status 200, :body "pong", :headers {"content-type" "text/plain"}})

(defn make-handler
  []
  (r/ring-handler
   (r/router
    ["/ping" {:get ping-handler}])))

(def config
  {:adapter/jetty {:handler (ig/ref :handler/router) :port 3000}
   :handler/router {}})

(defmethod ig/init-key :adapter/jetty [_ {:keys [handler] :as opts}]
  (run-jetty handler (-> opts (dissoc :handler) (assoc :join? false))))

(defmethod ig/init-key :handler/router [_ {:keys [oauth]}]
  (make-handler))

(defmethod ig/halt-key! :adapter/jetty [_ server]
  (.stop server))

(defn -main [& _]
  (ig/init config))
