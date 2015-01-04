(ns crud.schema
 (:require [schema.core :as s :refer [Str Num Inst Int Bool Keyword]]
           [schema.coerce :refer [coercer string-coercion-matcher]]
           [schema.utils]
           [integrity.datomic :as dat]))

(defn- validator [schema]
  (fn [value]
    ((coercer schema string-coercion-matcher) value)))

(defn optionalize
  "Create a new schema from `schema` where all keys have been replaced with 'optional' versions of themselves"
  [schema]
  (into {} (map (fn [[name type]]
                  [(s/optional-key name) type])
                (seq schema))))

(defn parsed-value [schema value]
  ((validator schema) value))


;; (defn find-referrer
;;   "Find the first referrer with the specified name in `refs`"
;;   [referrer refs]
;;   (first (filter #(= (:referrer %) referrer) refs)))

;; (defn coerce-id
;;   "Coerce the string-value of `id` to the type indicated by the :id attribute of `schema`"
;;   [schema id]
;;   (let [c (coercer (apply hash-map (find schema :id)) string-coercion-matcher)]
;;     (:id (c {:id id}))))

;; (defn build-ref
;;   "Builds a reference to the `referent` attribute of `resource` from the `referrer` attribute of
;; the current context"
;;   [resource referrer referent]
;;   {:referrer referrer
;;    :referent referent
;;    :resource resource
;;    :as-response (fn [entity]
;;                   (format "%s/%s"
;;                           (:name resource)
;;                           ((comp referent referrer) entity)))
;;    :as-lookup-ref (fn [uri]
;;                     (let [[_ id] (take-last 2 (clojure.string/split (.getPath (URI. uri)) #"/"))
;;                           coerce (coercer (apply hash-map (find (:schema resource) referent))
;;                                           string-coercion-matcher)]
;;                       [referent (referent (coerce {referent id}))]))})
      

