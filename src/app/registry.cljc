(ns app.registry
  (:require [malli.core :as m]
            [malli.util :as mu]
            [malli.registry :as mr]))

(def registry (merge (m/default-schemas) (mu/schemas)))

(mr/set-default-registry! registry)
