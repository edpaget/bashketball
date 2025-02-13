(ns app.server
  (:require [reitit.ring :as r]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.json :refer [wrap-json-response
                                          wrap-json-body]]
            [integrant.core :as ig]
            [app.authn :as authn]
            [app.dynamo :as ddb]
            [app.graphql :as graphql])
  (:gen-class))

(defn ping-handler [_]
  {:status 200 :body "pong" :headers {"content-type" "text/plain"}})

(defn make-handler
  [authenticator schema-handler]
  (r/ring-handler
   (r/router
    [["/ping" {:get ping-handler}]
     ["/graphql" {:post schema-handler
                  :middleware [wrap-json-response
                               wrap-json-body]}]])
   nil
   {:middleware [(partial authn/authenticate authenticator)]}))

(def config
  {:adapter/jetty {:handler (ig/ref :handler/router) :port 3000}
   :handler/router {:auth (ig/ref :auth/authenticator)
                    :schema-handler (ig/ref :graphql/schema-handler)}
   :auth/authenticator {:dynamo (ig/ref :db/dynamo)}
   :graphql/schema-handler {}
   :db/dynamo {:is-localstack? true}})

(defmethod ig/init-key :adapter/jetty [_ {:keys [handler] :as opts}]
  (run-jetty handler (-> opts (dissoc :handler) (assoc :join? false))))

(defmethod ig/init-key :handler/router [_ {:keys [auth schema-handler]}]
  (make-handler auth schema-handler))

(defmethod ig/init-key :db/dynamo [_ opts]
  (ddb/make-client opts))

(defmethod ig/init-key :auth/authenticator [_ opts]
  (authn/make-authenticator opts))

(defmethod ig/init-key :graphql/schema-handler [_ opts]
  (graphql/make-schema-handler))

(defmethod ig/halt-key! :adapter/jetty [_ server]
  (.stop server))

(defn -main [& _]
  (ig/init config))
