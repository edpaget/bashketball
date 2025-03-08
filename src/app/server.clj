(ns app.server
  (:require [reitit.ring :as r]
            [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.json :as ring.json]
            [ring.middleware.cookies :as ring.cookies]
            [ring.util.response :as ring.response]
            [integrant.core :as ig]
            [app.authn.middleware :as authn]
            [app.dynamo :as ddb]
            [app.graphql :as graphql]
            [app.registry])
  (:gen-class))

(defn ping-handler [_]
  {:status 200 :body "pong" :headers {"content-type" "text/plain"}})

(defn create-index-handler
  "Create a handler to render index.html on any request."
  ([]
   (create-index-handler {}))
  ([{:keys [index-file root]
     :or {index-file "index.html"
          root "public"}}]
   (letfn [(index-handler-fn
             [_request]
             (-> index-file
                 (ring.response/file-response {:root root})
                 (ring.response/content-type "text/html")))]
     (fn
       ([request]
        (index-handler-fn request))
       ([request respond _]
        (respond (index-handler-fn request)))))))

(defn make-handler
  [{:keys [session-authenticator
           session-creator
           schema-handler]}]
  (r/ring-handler
   (r/router
    [["/ping" {:get ping-handler}]
     ["/authn" {:post session-creator
                :middleware [ring.json/wrap-json-body
                             ring.cookies/wrap-cookies]}]
     ["/graphql" {:post schema-handler
                  :middleware [ring.json/wrap-json-response
                               ring.json/wrap-json-body
                               ring.cookies/wrap-cookies

                               (authn/wrap-session-authn session-authenticator)]}]])
   (r/routes
    (r/create-file-handler {:path "/js" :root "public/js"})
    (r/create-file-handler {:path "/main.css" :root "public/main.css"})
    (create-index-handler)
    (r/create-default-handler))))

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
