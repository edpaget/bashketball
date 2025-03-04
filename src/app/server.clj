(ns app.server
  (:require [reitit.ring :as r]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.json :as ring.json]
            [ring.middleware.cookies :as ring.cookies]
            [integrant.core :as ig]
            [app.authn.middleware :as authn]
            [app.dynamo :as ddb]
            [app.graphql :as graphql]
            [app.registry])
  (:gen-class))

(defn ping-handler [_]
  {:status 200 :body "pong" :headers {"content-type" "text/plain"}})

(defn make-handler
  [{:keys [session-authenticator
           session-creator
           schema-handler]}]
  (r/ring-handler
   (r/router
    [["/ping" {:get ping-handler}]
     ["/authn" {:post session-creator
                :middleware [ring.json/wrap-json-body]}]
     ["/graphql" {:post schema-handler
                  :middleware [ring.json/wrap-json-response
                               ring.json/wrap-json-body]}]])
   nil
   {:middleware [ring.cookies/wrap-cookies
                 (authn/wrap-session-authn session-authenticator)]}))

(def config
  {:adapter/jetty {:handler (ig/ref :handler/router) :port 3000}
   :handler/router {:session-authenticator (ig/ref :auth/session-authenticator)
                    :session-creator (ig/ref :auth/session-creator)
                    :schema-handler (ig/ref :graphql/schema-handler)}
   :auth/token-authenticator {:dynamo (ig/ref :db/dynamo)}
   :auth/session-authenticator {:dynamo (ig/ref :db/dynamo)}
   :auth/session-creator {:dynamo (ig/ref :db/dynamo)
                          :authenticator (ig/ref :auth/token-authenticator)}
   :graphql/schema-handler {}
   :db/dynamo {:is-localstack? true}})

(defmethod ig/init-key :auth/session-authenticator [_ opts]
  (authn/make-session-authenticator opts))

(defmethod ig/init-key :auth/token-authenticator [_ opts]
  (authn/make-token-authenticator opts))

(defmethod ig/init-key :auth/session-creator [_ opts]
  (authn/create-session opts))

(defmethod ig/init-key :adapter/jetty [_ {:keys [handler] :as opts}]
  (run-jetty handler (-> opts (dissoc :handler) (assoc :join? false))))

(defmethod ig/init-key :handler/router [_ opts]
  (make-handler opts))

(defmethod ig/init-key :db/dynamo [_ opts]
  (ddb/make-client opts))

(defmethod ig/init-key :graphql/schema-handler [_ opts]
  (graphql/make-schema-handler))

(defmethod ig/halt-key! :adapter/jetty [_ server]
  (.stop server))

(defn -main [& _]
  (ig/init config))
