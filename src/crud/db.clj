(ns crud.db
  (:require [datomic.api :as d]
            [clojure.walk]
            [integrity.datomic :as dat] ; import this dependency?
            [schema.core :as s :refer [Str Num Inst Int Bool Keyword]]
            [clojure.tools.trace :refer [trace]])
  (:import [java.net URL URI]))

(defn db-dispatch [db & args]
  (:db-type db))

(defmulti has-attr? db-dispatch)
(defmulti find-by-id db-dispatch)

(def ^{:private true} type-map
  {Str                      :db.type/string
   Bool                     :db.type/boolean
   Long                     :db.type/long
   ;java.Math.BigInteger     :db.type/bigint
   Num                      :db.type/double
   Int                      :db.type/long
   Float                    :db.type/float
   Inst                     :db.type/instant

   URI                      :db.type/string})


(defn- find-referrer
  "Find the first referrer with the specified name in `refs`"
  [referrer refs]
  (let [referrer= (fn [ref]
                    (if (= (:referrer ref) referrer)
                      ref))]
    (some referrer= refs)))

(defn- branch?
  "Return true if `v` has sub-elements"
  [v]
  (some #{(class v)} [clojure.lang.PersistentArrayMap clojure.lang.PersistentHashMap]))

(defn- attributes-for
  "Generate datomic attributes for the specified resource.

If `uniqueness` is specified, it should be a hash where each key means
  the attribute with that name is marked as unique in datomic. The
  value can be used to determine the type of uniqueness. For details
  about the different types of uniqueness, refer to the datomic
  documentation which can be found at

http://docs.datomic.com/identity.html

`refs` should be a sequence of attributes which represent references
to other entities. For each ref, at attribute of type :db.type/ref will
be generated.

`storage-attrs` should be a sequence of attributes representing some sub-set
of the attributes listed in `schema`. You can use a map in place of an
attribute to perform a transform on that attribute before persisting it. If
the value is a map, it should have `:name` and `:callable` attributes."
  [schema uniqueness refs storage-attrs]
  (letfn [(storable? [k]
            (or (nil? storage-attrs)
                (some #{k} (map #(if (map? %) (:name %) %) storage-attrs))))

          (generate-ref [k v]
            (merge (generate-attr k v)
                   {:db/valueType :db.type/ref}))
          
          (generate-attr [k v]
            (let [cardinality (if (vector? v)
                                :db.cardinality/many :db.cardinality/one)
                  value-type (get type-map v :db.type/string)]
              (merge
               {:db/id (d/tempid :db.part/db)
                :db/ident k
                :db/valueType value-type
                :db/cardinality cardinality
                :db.install/_attribute :db.part/db}
               (if-let [uniq (k uniqueness)] {:db/unique uniq} {}))))

          (reducer [acc [k v]]
            (cond
             (find-referrer k refs)
             (into acc [(generate-ref k v)])

             (branch? v)
             (into acc (conj (generate-attrs (into {} v))
                             ((:attr-factory dat/Ref) k)))

             :else (if (storable? k)
                     (into acc [(generate-attr k v)])
                     acc)))
          
          (generate-attrs [schema]
            (reduce reducer [] (seq schema)))]
    (generate-attrs schema)))

(defn make-fact [entity tmp-id k v]
  (let [value (if-let [ref (find-referrer k (:refs entity))]
                ;; k,v represents a reference to another entity so generate
                ;; a lookup-ref
                (condp instance? v
                  datomic.db.DbId v
                  ((:as-lookup-ref ref) v))
                
                ;; k,v represent an entity "leaf" value so just use v
                v)]
    [[:db/add tmp-id k value]]))

;;   (ensure-schema [db entities]
;;     "Ensure a schema exists on the connected DB for the specified entities")

;;   (creator! [db entity]
;;     "Returns a function that creates, validates and persists an instance of the
;; specified entity")

;;   (destroyer! [db entity]
;;     "Returns a function that deletes an instance of entity")

;;   (find-by-id [db entity]
;;     "Returns a function which finds an instance of entity by the id found in the
;; specified context")

;;   (find-entities [db params]
;;     "Find all entities in db that match the specified params")

;;   (as-response [db entity]
;;     "Returns a function which can generate as response for entity found in
;; the specified context"))

(defn- only
  "Return the only item from a query result"
  [query-result]
  (assert (= 1 (count query-result)))
  (assert (= 1 (count (first query-result))))
  (ffirst query-result))

(defn- qe
  "Returns the single entity returned by a query."
  [db query & args]
  (let [res (apply d/q query db args)]
    (if (empty? res)
      nil
      (d/entity db (only res)))))

(defn- find-by
  "Returns the unique entity identified by attr and val."
  [db attr val]
  (qe db '[:find ?e
           :in $ ?attr ?val
           :where [?e ?attr ?val]]
      (d/entid db attr) val))

(defn datomic-db [& {:keys [uri entities seed-data]}]
  (let [conn (do (d/create-database uri)
                   (d/connect uri))]

    ;; populate db with attributes for the specified entities
    @(d/transact conn (->> entities
                           (map (juxt :schema :uniqueness :refs :storable))
                           (map (partial apply attributes-for))
                           (mapcat identity)
                           distinct
                           vec))

    {:db-type :datomic
     :connection conn
     :entities entities
     :uri uri
     :current-db #(d/db conn)}))

(defmethod has-attr? :datomic [db attr-ident]
  (d/attribute ((:current-db db)) attr-ident))

;; (let [factify-node (fn [k v]
;;                      (into acc [[:db/add tmp-id k (if-let [ref (find-referrer k refs)]
;;                                                 (condp instance? v
;;                                                   datomic.db.DbId v
;;                                                   ((:as-lookup-ref ref) v))
;;                                                 v)]]))
                       
;; (defmethod creator! :datomic [db entity]
;;   (fn [value]
;;     (let [root-id (d/tempid :db.part/user)]
;;       @(d/transact (:connection db) 
                 
