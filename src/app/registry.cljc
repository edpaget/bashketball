(ns app.registry
  (:require [malli.core :as m]
            [malli.util :as mu]
            [malli.registry :as mr]))

(defonce type-registry (atom {}))

(defn register-type! [type ?schema]
  (swap! type-registry assoc type ?schema))

(mr/set-default-registry! (mr/composite-registry
                           (merge (m/default-schemas) (mu/schemas))
                           (mr/mutable-registry
                            type-registry)))
