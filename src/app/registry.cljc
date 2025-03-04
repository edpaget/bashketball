(ns app.registry
  (:require [malli.core :as m]
            [malli.util :as mu]
            [malli.experimental.time :as malli.time]
            [malli.registry :as mr]))

(defonce type-registry (atom {}))

(defn register-type! [type ?schema]
  (swap! type-registry assoc type ?schema))

(mr/set-default-registry! (mr/composite-registry
                           (merge (m/default-schemas) (mu/schemas) (malli.time/schemas))
                           (mr/mutable-registry
                            type-registry)))
