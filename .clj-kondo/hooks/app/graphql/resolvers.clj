(ns hooks.app.graphql.resolvers
  (:require [clj-kondo.hooks-api :as api]))

(defn defresolver
  "Clj-kondo hook for app.graphql.server/defresolver macro.
  Transforms (defresolver :Op/name schema fn-args fn-body...)
  into (fn fn-args fn-body...)."
  [{:keys [node]}]
  (let [children (:children node)]
    ;; The macro form is (defresolver operation-kw ?schema & fn-body)
    ;; Children:
    ;; 0: defresolver symbol
    ;; 1: operation-kw node
    ;; 2: ?schema node
    ;; 3: ?docstring node
    ;; 4+: fn-body nodes (e.g., arg-vector, body-forms... or multi-arity bodies)
    (when (> (count children) 4) ; Must have at least defresolver symbol, operation-kw, and ?schema.
      (let [[_ operation-kw-node doc-string schema & body] children
            ;; Determine the function name for the generated defn
            kw-val (api/sexpr operation-kw-node)
            schema-val (if (string? (api/sexpr doc-string))
                         schema
                         doc-string)
            fn-name-str (str (namespace kw-val) "-" (name kw-val) "-resolver")
            fn-name-sym (api/token-node (symbol fn-name-str))
            fn-body-nodes (if (string? (api/sexpr doc-string))
                            body
                            (list* schema body))]

        ;; Construct the (fn fn-name-sym ...fn-body-nodes...) form
        {:node
         (api/list-node
          (list (api/token-node 'with-meta)
                (api/vector-node [operation-kw-node
                                  (api/list-node
                                   (list* (api/token-node 'fn)
                                          fn-name-sym
                                          fn-body-nodes))])
                (api/map-node [(api/keyword-node :schema) schema-val])))}))))
