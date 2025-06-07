(ns app.graphql.resolvers
  (:require
   [app.registry :as registry]
   [app.card.graphql.resolvers]
   [com.walmartlabs.lacinia.resolve :as lacinia.resolve]
   [malli.core :as mc]
   [app.graphql.transformer :as gql.transformer]
   [clojure.tools.logging :as log]
   [malli.error :as merr]))

(defn register-resolver! [target-ns graphql-operation schema fn-sym & {:keys [doc-string]}]
  (registry/register-type! graphql-operation schema)
  (let [ns-resolvers-var (ns-resolve target-ns 'ns-resolvers-map)
        resolver-var (ns-resolve target-ns fn-sym)]
    (alter-var-root ns-resolvers-var assoc graphql-operation (with-meta [schema resolver-var]
                                                               {:doc doc-string}))))

(defn ->argument-type
  "Gets the second argument to the function schema"
  [schema _ children _]
  (case (mc/type schema)
    :=> (first children)
    :cat (second children)
    schema))

(defn coerce-graphql-args
  "Given a malli schema wrap a funtion and apply malli coerce to it's second argument
  returning a formatted graphql error if coercion fails."
  [arg-type f]
  (fn [arg1 arg2 arg3]
    (try
      (let [coerced (gql.transformer/coerce (or arg2 {}) arg-type)]
        (f arg1 coerced arg3))
      (catch Throwable e
        (let [data (ex-data e)]
          (if-not (= ::mc/coercion (:type data))
            (throw e)
            (do
              (log/error e "failed to coerce arguments")
              (lacinia.resolve/resolve-as nil (-> (ex-data e)
                                                  :data
                                                  :explain
                                                  merr/humanize
                                                  (assoc :message "failed to validate arguments"))))))))))

(defmacro defresolver
  "Define a register a new graphql resolver function. Takes a keyword for the resolver
  name. Otherwise defines a three argument function and adds the function and its malli
  schema to the resolver registry."
  [operation-kw ?doc-string ?schema & fn-body]
  (when-not (keyword? operation-kw)
    (throw (IllegalArgumentException.
            (str "First argument to defresolver must be a literal keyword. Got: " (pr-str operation-kw)))))

  (let [fn-name (symbol (str (namespace operation-kw)
                             "-" (name operation-kw)
                             "-resolver"))
        fn-body (if (string? ?doc-string)
                  fn-body
                  (list* ?schema fn-body))
        schema (if (string? ?doc-string)
                 ?schema
                 ?doc-string)
        opts (when (string? ?doc-string)
               {:doc ?doc-string})]
    `(do
       ;; Ensure the ns-resolvers-map var exists in the calling namespace.
       ;; It's an atom holding the map of resolvers for this namespace.
       ;; The var name 'ns-resolvers-map is by convention.
       (defonce ~'ns-resolvers-map {})
       (defonce ~fn-name (coerce-graphql-args (mc/walk ~schema ->argument-type)
                                              (fn ~@fn-body)))
       (register-resolver! *ns* ~operation-kw ~schema '~fn-name ~opts))))

(defn ns-gql-resolvers
  "Retrieve the map of resolvers from a namespace"
  [target-ns]
  (if-let [ns-resolvers-var @(ns-resolve target-ns 'ns-resolvers-map)]
    ns-resolvers-var
    (throw (ex-info (str "No ns-resolvers-map var found in namespace: " target-ns) {:ns target-ns}))))

(defn get-resolver-fn
  "Retrieves a resolver function by its keyword from the specified namespace.
  target-ns : The namespace (e.g., 'app.card.graphql.resolvers).
  resolver-kw: The keyword identifying the resolver (e.g., :Query/card)."
  [target-ns resolver-kw]
  (-> (ns-gql-resolvers target-ns)
      (get resolver-kw)
      second))

(defmacro alias-resolver
  "Take a target resolver-kw and a list of other kws that should use the same definition
  and update the resolver map with their names
  "
  [target-kw & alias-kws]
  (let [resolvers (ns-gql-resolvers *ns*)]
    (doseq [alias alias-kws]
      (alter-var-root (ns-resolve *ns* 'ns-resolvers-map)
                       assoc alias (get resolvers target-kw)))))
