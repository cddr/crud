(ns crud.entity
  "An entity is a just set of attributes around which we can define basic CRUD-like behaviour.

  Using `defentity` we can declare independently of the underlying database, how it relates to other entities,
  which attributes should actually be persisted to the database, any validations necessary to ensure data integrity
  (including which if any attributes define an entity's primary key)"
  (:require [schema.core :as s :refer [Str Num Inst Int Bool Keyword Any]]
            [schema.coerce :as c]
            [crud.db :refer [find-by]]
            [bidi.bidi :refer [path-for]]
            [clojure.string :refer [join lower-case split]]
            [clojure.edn :as edn])
  (:import [java.net URL URI]))

(def routes
  ["/"
   {[:entity "/" :id] :resource
    [:entity] :collection
    "" :index}])

(defn locate [uri rel]
  (let [path (.getPath (URI. uri))
        route-params (:route-params (bidi.bidi/match-route routes uri))]
    [(keyword rel) (edn/read-string (:id route-params))]))

;; ## Entities
;;
;; Entities are your business entities. User, Account, Order, LineItem etc. Use entities to define
;; your business entities and the relationships between them. All entities have 

(defprotocol EntityDefinition
  (read-id [entity id-str]
    "Try to coerce `id-str` to the schema type specified by :id key of `(:schema entity)`

  Returns nil if the coercion fails")

  (link-schema [entity]
    "Construct a schema that defines a valid attempt to create an instance of this entity") 

  (query-schema [entity]
    "Construct a schema supporting queries represented by (key,value) pairs")

  (storage-schema [entity]
    "Constructs the storable part of `entity`'s schema")

  (storable-value [entity value]
    "Constructs a new value from the storable parts of `value` (according to `entity`)

  Specifically, if the :storable key of entity is nil, this returns `value`. Otherwise, we use the storage agents to
  construct a new value with some entries potentially added/removed/transformed")

  (find-link-from [entity attr-name]
    "Given an `entity`, find the link from `name`"))

;; ## Storage Agents
;; 
;; Storage Agents may be used to transform values before persisting them to the database. This is useful if you
;; need to encrypt secrets or canonicalize addresses to name just two examples. The `:callable` key should be a
;; function of arity 1 which transforms a value so that it can be stored. The `:name` key represents the attribute
;; to which the transformed value will be assigned.

(def StorageAgent
  "Schema representing a storage agent

  Storage agents may be used to transform values before persisting them to the database (e.g. encrypt secrets,
  canonicalize addresses etc)"
  (s/either Keyword
            {:name Keyword
             :callable ifn?}))

(defn storage-agent
  "Constructs the storage agent specified by `form`"
  [form]
  (if (keyword? form)
    {:name form
     :callable form}
    form))

(defrecord Entity [schema links storable uniqueness]
  EntityDefinition
  (read-id [entity id-str]
    (let [id-reader (-> (get (:schema entity) :id)
                        (c/coercer c/string-coercion-matcher))]
      (id-reader id-str)))

  (link-schema [entity]
    (assoc (:schema entity)
      (s/optional-key :_links) s/Any))

  (query-schema [entity]
    (let [optionalize (fn [[name type]]
                        [(s/optional-key name) type])]
      (->> (seq (:schema entity))
           (map optionalize)
           (into {}))))

  (storage-schema [entity]
    (if (:storable entity)
      (select-keys (:schema entity)
                   (->> (:storable entity)
                        (map storage-agent)
                        (map :name)))
      (:schema entity)))

  (storable-value [entity value]
    (let [extract (fn [agent]
                    (let [k (:name agent)]
                      [k ((:callable agent) value)]))]
      (if (:storable entity)
        (->> (:storable entity)
             (map storage-agent)
             (mapcat extract)
             (apply hash-map))
        value)))

  (find-link-from [entity name]
    (let [{links :links} entity
          matched (fn [link]
                    (if (= (:from link) name)
                      link))]
      (some matched links))))

(defn entity [name options]
  (map->Entity (merge {:name name} options)))

(defmacro defentity
  "Defines an entity

`:schema` should be a map (possibly nested) that represents any schematic constraints required by this entity. It
  is currently assumed that the schema will be a Prismatic schema but in the future, I'd like to add support for
  other schema specification syntaxes (e.g. herbert) (PATCHES welcome :-))

`:links` should be a sequence of maps that represent the relationships in which this entity is involved. Each link
  must conform to the `Link` schema. When `find-by` returns a value, it will have keys for each Link. The `link`
  function provides syntax sugar for constructing Links.

`:storable` should be a sequence of maps that representing the storable part of the entity. Transformations may be
  represented using storage agents which are just maps with :name and :callable keys

`:uniqueness` if specified should be a map where each key represents a unique attribute. As long as it's 'truthy'
  CrudDB implementations are free to further refine the definition of uniqueness in an implementation specific way
"
 [entity-name & body]
 (let [fmt-name (fn [x]
                  (str x))]
                   ;; (join "-" (map lower-case
                   ;;                (split (str x) #"(?=[A-Z])"))))]
   `(def ~entity-name
      (map->Entity (merge {:name ~(fmt-name entity-name)}
                          (hash-map ~@body))))))

;; ## Links
;;
;; Links put the 'hyper' in hyper-media. In generating the hyper-media representation of entities, we use
;; any links between entities to generate hyper-media links. This is a "meta-object protocol" of sorts for
;; the web and allows clients to explore the API programatically.

(def Link
  "Schema representing an association between entities"
  {:from Keyword
   :to Any
   :attr Keyword
   :cardinality Keyword})

(defn link
  "Represent a link `from` to `attr` of `to` with optional `cardinality`"
  ([from [to attr] cardinality]
   {:from from
    :to to
    :attr attr
    :cardinality cardinality})

  ([from [to attr]]
    (link from [to attr] :db.cardinality/one)))

(defn find-link-to
  "Given `entity`, find the link to `other` (which should be an entity)"
  [entity other]
  (let [{links :links} entity
        matched (fn [link]
                  (if (= (:to link) other)
                    link))]
    (some matched links)))

(defn publish-link [route-id options]
  (merge
   {:href (apply path-for routes route-id (flatten (seq options)))}
   (select-keys options [:name :href :rel :title])))

(defn collection-links [db entity pred]
  (let [entity-name (:name entity)
        self (path-for routes :collection :entity entity-name)
        itemize (fn [item]
                  {:rel "item"
                   :href (path-for routes :resource
                                   :entity entity-name
                                   :id (:id item))})]
    {:_links (concat [(publish-link :collection {:entity (:name entity)
                                                 :rel "self"})
                      (publish-link :collection {:entity (:name entity)
                                                 :rel "create"})]
                     (->> (filter pred (find-by db {:entity entity-name}))
                          (map itemize)))}))

(defn resource-links [db entity value]
  {:_links [(publish-link :resource {:rel "self",
                                     :entity (:name entity),
                                     :id (:id value)})
            (publish-link :collection {:rel "collection",
                                       :entity (:name entity)})]})

;; (defprotocol Historian
;;   "Historian provides an API for retrieving the history of changes made
;; to the entity identified by `id`"
;;   (change-log [db entity id]
;;     "Gather the history associated with the entity identified by `id`"))

;; (defprotocol AsynchronousValidator
;;   "The validation function is called asynchronously for each transaction and gets to
;; add hints, warnings and errors in relation to entity as it existed immediately after
;; the transaction"
;;   (validate [db-after id])
;;   (hints [db id]
;;     "Return any hints associated with the entity at `id`")
;;   (warnings [db id]
;;     "Return any warnings associated with the entity at `id`")
;;   (errors [db id]
;;     "Return any errors associated with the entity at `id`"))



;; ;; (defn find-by-id [entity id]
;; ;;   {:entity entity
;; ;;    :


;; (defn evalue [entity value]
;;   {:entity entity
;;    :value value})

;; (defn with-entity [entity value]
;;   (with-meta value
;;     {:entity entity}))

