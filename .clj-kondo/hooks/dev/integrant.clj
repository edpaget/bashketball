(ns hooks.dev.integrant
  (:require [clj-kondo.hooks-api :as api]))

(defn with-system
  "Provides a clj-kondo hook for the dev.integrant/with-system macro.
  The macro has the form (with-system [bindings-form & system-edn-file-string] & body).
  It internally creates a system map, and `bindings-form` destructures this system map.
  This hook transforms the macro call into a (let [bindings-form <placeholder>] ...body)
  form for clj-kondo's analysis, allowing it to understand the symbols introduced
  by `bindings-form`."
  [{:keys [node]}]
  (let [[_macro-name macro-args-vector-node & body-nodes] (:children node)]
    (if-not (and macro-args-vector-node
                 (api/vector-node? macro-args-vector-node)
                 (seq (:children macro-args-vector-node)))
      ;; The first argument to the macro should be a vector containing at least the bindings-form.
      ;; If not, pass through for clj-kondo to report or handle.
      {:node node}
      (let [;; The actual bindings-form (e.g., {:keys [db config]}) is the first element
            ;; of the macro-args-vector-node.
            bindings-form-node (first (:children macro-args-vector-node))
            ;; Create a placeholder symbol for the system map that bindings-form-node destructures.
            system-map-placeholder (api/keyword-node :integrant-system-map-placeholder)
            ;; Construct the new binding vector for the let: [bindings-form-node system-map-placeholder]
            let-bindings (api/vector-node [bindings-form-node system-map-placeholder])]
        {:node (api/list-node
                (list* (api/token-node 'let) ; let
                       let-bindings          ; [bindings-form-node placeholder]
                       body-nodes))}))))     ; body
