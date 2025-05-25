(ns app.graphql.resolvers
  (:require
   [app.registry :as registry]))

(def resolvers-registry (atom {}))

(defn register-resolver! [graphql-operation schema fn & {:keys [doc-string]}]
  (registry/register-type! graphql-operation schema)
  (swap! resolvers-registry assoc graphql-operation (with-meta [schema fn]
                                                      {:doc doc-string})))
(defmacro defresolver
  "Define a register a new graphql resolver function. Takes a keyword for the resolver
  name that must be qualified with either Query/ or Mutation/. Otherwise defines a three argument
  function and adds the function and its malli schema to the resolver registry."
  [operation-kw ?doc-string ?schema & fn-body]
  (when-not (keyword? operation-kw)
    (throw (IllegalArgumentException.
            (str "First argument to defresolver must be a literal keyword. Got: " (pr-str operation-kw)))))

  (let [kw-ns (namespace operation-kw)]
    (when-not (or (= "Query" kw-ns) (= "Mutation" kw-ns))
      (throw (IllegalArgumentException.
              (str "The keyword " (pr-str operation-kw) " must be qualified with Query/ or Mutation/. "
                   (if kw-ns
                     (str "Actual namespace: " kw-ns ".")
                     "It has no namespace."))))))

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
    `(register-resolver! ~operation-kw ~schema (fn ~fn-name ~@fn-body) ~opts)))

(defn get-resolver-fn
  [kw]
  (second (kw @resolvers-registry)))
