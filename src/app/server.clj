(ns app.server
  (:require [reitit.ring :as r]
            [ring.adapter.jetty :refer [run-jetty]]
            [integrant.core :as ig]
            [app.authn :as authn]
            [app.dynamo :as ddb])
  (:gen-class))

(defn ping-handler [_]
  {:status 200 :body "pong" :headers {"content-type" "text/plain"}})

(defn make-handler
  [authenticator]
  (prn "HERE")
  (r/ring-handler
   (r/router
    ["/ping" {:get ping-handler}])
   nil
   {:middleware [(partial authn/authenticate authenticator)]}))

(def config
  {:adapter/jetty {:handler (ig/ref :handler/router) :port 3000}
   :handler/router {:auth (ig/ref :auth/authenticator)}
   :auth/authenticator {:dynamo (ig/ref :db/dynamo)}
   :db/dynamo {:is-localstack? true}})

(defmethod ig/init-key :adapter/jetty [_ {:keys [handler] :as opts}]
  (run-jetty handler (-> opts (dissoc :handler) (assoc :join? false))))

(defmethod ig/init-key :handler/router [_ {:keys [auth]}]
  (make-handler auth))

(defmethod ig/init-key :db/dynamo [_ opts]
  (ddb/make-client opts))

(defmethod ig/init-key :auth/authenticator [_ opts]
  (authn/make-authenticator opts))

(defmethod ig/halt-key! :adapter/jetty [_ server]
  (.stop server))

(defn -main [& _]
  (ig/init config))
