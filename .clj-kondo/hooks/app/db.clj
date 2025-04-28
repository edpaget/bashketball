(ns hooks.app.db
  (:require [clj-kondo.hooks-api :as api]))

(defn with-transaction
  "Hook for app.db/with-transaction macro.
  Treats (with-transaction [tx db-spec] body...) like (let [tx db-spec] body...).
  Treats (with-transaction [tx] body...) like (let [tx nil] body...)."
  [{:keys [node]}]
  (let [[_ bindings & body] (:children node)
        bindings-children (when (api/vector-node? bindings)
                            (:children bindings))
        bindings-count (count bindings-children)]
    (cond
      (and (= 2 bindings-count) (seq body))
      (let [[binding-sym binding-val] bindings-children]
        {:node (api/list-node
                (list* (api/token-node 'let)
                       (api/vector-node [binding-sym binding-val])
                       body))})

      (and (= 1 bindings-count) (seq body))
      (let [[binding-sym] bindings-children]
        ;; Treat the single binding like `(let [tx nil] ...)`
        ;; The actual value doesn't matter for linting the binding itself.
        {:node (api/list-node
                (list* (api/token-node 'let)
                       (api/vector-node [binding-sym nil])
                       body))})

      :else
      (api/reg-finding! (assoc (meta node)
                               :message "app.db/with-transaction expects a vector of [symbol] or [symbol expression] and a body."
                               :type :kondo.lint/invalid-arity)))))
