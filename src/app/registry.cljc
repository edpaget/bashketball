(ns app.registry
  (:require
   [malli.core :as m]
   [malli.experimental.time :as malli.time]
   [malli.registry :as mr]
   [malli.util :as mu]))

(defonce type-registry (atom {}))

(defn register-type! [type ?schema]
  (swap! type-registry assoc type ?schema))

(defmacro defschema
  "Define a new type and add it to the registry"
  {:clj-kondo/lint-as 'clojure.core/def}
  [type schema]
  (register-type! ~type ~schema))

(mr/set-default-registry! (mr/composite-registry
                           (merge (m/default-schemas) (mu/schemas) (malli.time/schemas))
                           (mr/mutable-registry
                            type-registry)))

(def ^:private -validator
  (memoize (fn [type-name]
             (m/validator type-name))))

(defn validate
  [type value]
  ((-validator type) value))
