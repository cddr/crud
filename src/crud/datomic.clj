(in-ns 'crud.resource)

(defn datomic-schema [resource]
  (let [{:keys [refs uniqueness schema]} resource]
    (letfn [(generate-ref [k v]
              (if-let [u (k uniqueness)]
                (do
                  (prn "uniqueness: " u)
                  (if-not (some #{u} [:db.unique/identity :db.unique/value])
                    (throw (new java.lang.Exception (str "invalid uniqueness:" u)))))) 
              (merge (dat/attribute k v (k uniqueness))
                     {:db/valueType :db.type/ref}))
            
            (generate-attr [k v]
              (dat/attribute k v (k uniqueness)))

            (reducer [acc [k v]]
              (cond
               (some #{k} (:refs resource))
               (into acc [(generate-ref k v)])

               (extends? schema.core/Schema (class v))
               (into acc [(generate-attr k v)])

               :else
               (into acc (conj (generate-attrs (into {} v) uniqueness)
                               ((:attr-factory dat/Ref) k)))))
            
            (generate-attrs [schema]
              (reduce reducer [] (seq schema)))]

      (generate-attrs schema))))

(defn datomic-facts
  ([tmp-id object]
     "Generates datomic facts by recursively walking the specified map converting
(key val) -> [:db/add tmp-id attr val]

For any values that are themselves maps, we recur on the map and add a ref for
the current key"
     (reduce (fn [acc [k v]]
               (if (map? v)
                 (into acc (let [ref-id (d/tempid :db.part/user)
                                 ret (concat [[:db/add tmp-id k ref-id]]
                                             (datomic-facts ref-id (into {} v)))]
                             ret))
                 (do
                   (into acc [[:db/add tmp-id k v]]))))
             []
             (seq object)))
  ([objects]
     (mapcat (fn [obj]
               (datomic-facts (d/tempid :db.part/user) obj))
             objects)))
