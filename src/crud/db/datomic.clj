(ns crud.db.datomic
  (:require [datomic.api :as d]
            [crud.entity :refer [Link storage-schema find-link-from collection-links
                                 resource-links locate]]
            [crud.db :refer :all]
            [clojure.walk]
            [integrity.datomic :as dat] ; import this dependency?
            [schema.core :as s :refer [Str Num Inst Int Bool Keyword]]
            [clojure.walk :as walk]
            [clojure.tools.trace :refer [trace trace-vars]])
  (:import [java.net URL URI]))

(def ^{:private true} type-map
  {Str                      :db.type/string
   Bool                     :db.type/boolean
   Long                     :db.type/long
   ;java.Math.BigInteger     :db.type/bigint
   Num                      :db.type/double
   Int                      :db.type/long
   Float                    :db.type/float
   Inst                     :db.type/instant

   Link                     :db.type/ref

   URI                      :db.type/string})

(defn- branch?
  "Return true if `v` has sub-elements"
  [v]
  (some #{(class v)} [clojure.lang.PersistentArrayMap clojure.lang.PersistentHashMap]))

(defn- cardinality [schema-val]
  (if (vector? schema-val)
    :db.cardinality/many
    :db.cardinality/one))

(defn- attr [unique? component? k v]
  (merge (if unique?
           {:db/unique unique?})

         {:db/id (d/tempid :db.part/db)
          :db/ident k
          :db/valueType (if (vector? v)
                          :db.type/ref
                          (get type-map v :db.type/string))
          :db/cardinality (cardinality v)
          :db/isComponent component?
          :db.install/_attribute :db.part/db}))
  
(defn entity-attributes [entity]
  "Generate datomic attributes for the specified resource.

If `uniqueness` is specified, each key represents a unique attribute. As long as
it's 'truthy' the value may be used by CrudDB in an implementation specific way

`links` should be a sequence of maps that match the `Link` schema. For each link,
at attribute of type :db.type/ref will be generated.

`storable` should be a sequence of attributes representing the storable
part of the entity. Transformations may be represented using storage
agents which are just maps with :name and :callable keys"
  (letfn [(factory [component?]
            (fn reducer [acc [k v]]
              (cond
                (vector? v) (into acc (conj (mapcat (partial mk-facts true) v)
                                            (attr (:uniqueness k) true k [Link])))
                (branch? v) (into acc (mk-facts true v))
                :else       (conj acc (attr (:uniqueness k) component? k v)))))

          (mk-facts [component m]
            (reduce (factory component) [] (seq m)))]

    (concat (mk-facts false (storage-schema entity))
            (->> (:links entity)
                 (map :from)
                 (map #(attr false false % Link))
                 vec))))

(defn facts-for [db entity value]
  (fn [root-id]
    (let [props (merge (select-keys (dissoc value :_links)
                                    (keys (:schema entity)))
                       {:db/id root-id}
                       {:entity (:name entity)})
          links (get value :_links)
          make-link (fn [lnk]
                      (trace "lnk" lnk)
                      (locate (:href lnk) (:rel lnk)))]
      (merge props
             (->> links
                  (map make-link)
                  (apply merge))))))

(defrecord DatomicCrudDB [uri connection entities]
  CrudDB
  (present [db entity]
    (collection-links db entity (constantly true)))

  (present [db entity value]
    (merge (resource-links db entity value)
           (dissoc (into {} value) :entity)))

  (has-attr? [db attr]
    (-> (d/db (:connection db))
        (d/entity attr)
        :db.install/_attribute))

  (commit! [db entity value]
    (trace "committing against: " db)
    (let [root-id (d/tempid :db.part/user)
          facts ((facts-for db entity value) root-id)]
      (clojure.tools.trace/trace @(d/transact (:connection db)
                                              (trace [facts])))
      db))

  (retract! [db entity value]
    @(d/transact (:connection db)
                 [[:db.fn/retractEntity (:db/id value)]])
    db)

  (find-by [db params]
    (clojure.tools.trace/trace "find-by xxx" [db params])
    (let [c (:connection db)
          db (d/db c)
          build-predicate (fn [[k v]] ['?e k v])
          q {:find '[?e]
             :in '[$]
             :where (map build-predicate params)}]
      (clojure.tools.trace/trace "q" q)
      (->> (apply concat (d/q q db))
           (map (partial d/entity db)))))

  (find-by-id [db id]
    (clojure.tools.trace/trace "find-by-id" id)
    (let [c (:connection db)]
      (first (find-by db {:id id})))))

(defmethod crud-db :datomic [db-spec]
  (let [{:keys [uri entities seed-data]} db-spec
        conn (do (d/create-database uri)
                   (d/connect uri))
        known? (fn [attr] (has-attr? (map->DatomicCrudDB {:connection conn}) (:db/ident attr)))
        attrs (->> entities
                   (map (partial entity-attributes))
                   (mapcat identity)
                   (filter (complement known?))
                   (group-by :db/ident)
                   (vals)
                   (map first))
        builtins [(attr false false :entity Str)]]

    ;; populate db with attributes for the specified entities
    @(d/transact conn (concat builtins attrs))

    (map->DatomicCrudDB {:connection conn
                         :entities entities
                         :uri uri})))

