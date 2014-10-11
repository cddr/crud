(in-ns 'crud.resource)

;; HTTP Helpers
(defn get? [ctx]
  (= :get (get-in ctx [:request :request-method])))

(defn known-content-type? [ctx]
  (if (= "application/edn" (get-in ctx [:request :headers "content-type"]))
    true
    [false {:error "Unsupported content type"}]))

;; Schema Helpers
(defn build-ref [resource referrer referent]
  "Builds a reference to the `referent` attribute of `resource` from the `referrer` attribute of
the current context"
  {:referrer referrer
   :referent referent
   :resource resource
   :as-response (fn [entity]
                  (format "%s/%s"
                          (:name resource)
                          ((comp referent referrer) entity)))
   :as-lookup-ref (fn [uri]
                    (let [[_ id] (clojure.string/split uri #"/")
                          coerce (coercer (apply hash-map (find (:schema resource) referent))
                                          string-coercion-matcher)]
                      [referent (referent (coerce {referent id}))]))})
      
(defn optionalize [schema]
  "TODO: consider optionalizing recursively"
  (into {} (map (fn [[name type]]
                  [(s/optional-key name) type])
                (seq schema))))



