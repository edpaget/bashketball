(ns app.db
  (:require
   [app.db.connection-pool :as db.pool]
   [integrant.core :as ig]))

(defmethod ig/init-key ::pool [_ {:keys [config]}]
  (db.pool/create-pool (:database-url config) (:c3p0-opts config)))

(defmethod ig/halt-key! ::pool [_ datasource]
  (db.pool/close-pool! datasource))
