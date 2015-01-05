(ns crud.resource
  (:require [clojure.string :refer [split]]
            [crud.db :as db]
            [liberator.representation :refer [Representation]])
  (:import [java.net URL URI]
           [java.io File]))

(defn as-url [resource]
  (let [{:keys [entity value]} resource
         name (:name entity)]
    (format "/%s/%s" name (:id value))))

(defmulti as-response #(:db-type %))

;; (defn as-lookup-ref [entity referent uri]
;;   (let [id (.getName (File. (.getPath uri)))
;;         schema (select-keys (:schema entity) [referent])]
;;     [referent (referent (parsed-value schema {referent id}))]))


  
;; (defmulti as-response
  
;;   (fn [entity value] (:backend entity))

;; (defmethod as-response :datomic [entity value]
  
  


;; (.getName (File. "/foo/bar"))

;; (with-meta {:foo "bar"}
;;   {:bar "yolo"})

;; (defn as-lookup-ref [entity-ref uri]
;;   (let [[_ id] (take-last 2 (split (.getPath (URI. uri)) #"/"))
;;         schema (apply hash-map (find :schema entity-ref




;; (defprotocol EntityDef
;;   (url-for [entity-def entity]))

;; (defn entity-def [impl]
;;   (reify EntityDef
;;     (url-for [entity-def value]
;;       (format "%s/%s" (:name entity-def)
;;               ((comp 


;; ;; (def refs [entity-def]
;; ;;   (map (fn [ref]
;; ;;          {:referrer
;; ;;   {:referrer referrer
;; ;;    :referent referent
;; ;;    :resource resource
;; ;;    :as-response (fn [entity]
;; ;;                   (format "%s/%s"
;; ;;                           (:name resource)
;; ;;                           ((comp referent referrer) entity)))
;; ;;    :as-lookup-ref (fn [uri]
;; ;;                     (let [[_ id] (take-last 2 (clojure.string/split (.getPath (URI. uri)) #"/"))
;; ;;                           schema (apply hash-map (find :schema resource) referent)]
;; ;;                       [referent (referent (parsed-value {referent id}))]))})
  

;; (defprotocol EntityDef
;;   (refs [entity-def] "List the entities referenced by this one"))
  

;; ;; (defmulti entity-refs class)

;; ;; (defmethod entity-refs Entity [entity

;; ;; (defmacro defentity [name & body]
;; ;;   `(def ~name
;; ;;      (map->Entity (merge {:name (clojure.string/lower-case (name '~name))}
;; ;;                          (hash-map ~@body)))))

;; ;; (defn entity? [object]

;; ;; (instance? Entity (map->Entity {:name "foo"}))


;; ;; (defn entity-ref
;; ;;   "Builds a reference to the `referent` attribute of `resource` from the `referrer` attribute of
;; ;; the current context"
;; ;;   [resource referrer referent]
;; ;;   {:referrer referrer
;; ;;    :referent referent
;; ;;    :resource resource
;; ;;    :as-response (fn [entity]
;; ;;                   (format "%s/%s"
;; ;;                           (:name resource)
;; ;;                           ((comp referent referrer) entity)))
;; ;;    :as-lookup-ref (fn [uri]
;; ;;                     (let [[_ id] (take-last 2 (clojure.string/split (.getPath (URI. uri)) #"/"))
;; ;;                           schema (apply hash-map (find :schema resource) referent)]
;; ;;                       [referent (referent (parsed-value {referent id}))]))})


;; ;; ;; (defmulti entity-response (fn [x y] [x y]))

;; ;; ;; (defn entity-response [entity entity-value]
;; ;; ;;   (reify liberator.representation/Representation
;; ;; ;;     (as-response [_ ctx]
;; ;; ;;       (let [{:keys [schema refs]} entity]
;; ;; ;;         (letfn [(walk-entity [entity schema]
;; ;; ;;             (reduce (fn [m [schema-name schema-type]]
;; ;; ;;                       (if (branch? schema-type)
;; ;; ;;                         (merge m {schema-name (walk-entity (schema-name entity)
;; ;; ;;                                                            {schema-name schema-type})})
;; ;; ;;                         (merge m (if-let [ref (find-referrer schema-name refs)]
;; ;; ;;                                    {schema-name ((:as-response ref) entity)}
;; ;; ;;                                    {schema-name (schema-name entity)}))))
;; ;; ;;                     {}
;; ;; ;;                     schema))]
;; ;; ;;           (as-response (walk-entity entity (seq schema)) context))))))

;; ;; ;; (defmethod serialize [resource ]
  
;; ;; ;; (defprotocol Entity
;; ;; ;;   (as-response [entity-value])
  
  

;; ;; ;; schema [entity] "TODO: describe schema")
;; ;; ;;   (uniqueness [entity] "How to identify a unique entity")
;; ;; ;;   (refs [entity] "Which attributes are references to other entities")
;; ;; ;;   (storable [entity] "Which attributes must be persisted to the database"))
  
