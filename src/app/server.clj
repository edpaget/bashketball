(ns app.server
  (:require
   [app.authz.middleware :as authz]
   [app.db :as db]
   [app.s3 :as s3]
   [integrant.core :as ig]
   [reitit.ring :as r]
   [ring.adapter.jetty :refer [run-jetty]]
   [ring.middleware.cookies :as ring.cookies]
   [ring.middleware.json :as ring.json]
   [ring.middleware.reload :as ring.reload]
   [ring.util.response :as ring.response]))

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
  [{:keys [authn-handler config db-pool graphql-handler s3-client]}]
  (letfn [(wrap-db-pool-binding [handler]
            (fn [req]
              (binding [db/*datasource* db-pool
                        s3/*s3-client* s3-client]
                (handler req))))]
    (r/ring-handler
     (r/router
      [["/ping" {:get ping-handler}]
       ["/authn" {:post authn-handler
                  :middleware [[ring.json/wrap-json-body {:keywords? true}]
                               ring.json/wrap-json-response
                               ring.cookies/wrap-cookies
                               wrap-db-pool-binding]}]
       ["/graphql" {:post graphql-handler
                    :middleware [ring.json/wrap-json-response
                                 [ring.json/wrap-json-body {:keywords? true}]
                                 ring.cookies/wrap-cookies
                                 wrap-db-pool-binding
                                 [authz/wrap-current-actor {:cookie-name (-> config :auth :cookie-name)}]]}]])
     (r/routes
      (r/create-file-handler {:path "/js" :root "public/js"})
      (r/create-file-handler {:path "/main.css" :root "public/main.css"})
      (create-index-handler)
      (r/create-default-handler)))))

(defmethod ig/init-key ::jetty [_ {:keys [handler reload? config] :as opts}]
  (let [thread-pool (doto (org.eclipse.jetty.util.thread.VirtualThreadPool.)
                      (.setTracking true))]
    (run-jetty (cond-> handler
                 reload? ring.reload/wrap-reload)
               (-> opts
                   (assoc :port (:app-port config))
                   (dissoc :handler :reload :config)
                   (assoc :join? false :thread-pool thread-pool)))))

(defmethod ig/init-key ::router [_ opts]
  (make-handler opts))

(defmethod ig/halt-key! ::jetty [_ server]
  (.stop server))
