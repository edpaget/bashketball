(ns app.auth
  (:require
   [app.authn.middleware :as authn]
   [integrant.core :as ig])
  )

(defmethod ig/init-key ::session-authenticator [_ opts]
  (authn/make-session-authenticator opts))

(defmethod ig/init-key ::token-authenticator [_ opts]
  (authn/make-token-authenticator opts))

(defmethod ig/init-key ::session-creator [_ opts]
  (authn/create-session opts))
