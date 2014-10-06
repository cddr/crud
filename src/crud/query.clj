(in-ns 'crud.resource)

(defn only
  "Return the only item from a query result"
  [query-result]
  (assert (= 1 (count query-result)))
  (assert (= 1 (count (first query-result))))
  (ffirst query-result))

(defn qe
  "Returns the single entity returned by a query."
  [query db & args]
  (let [res (apply d/q query db args)]
    (if (empty? res)
      nil
      (d/entity db (only res)))))

(defn find-by
  "Returns the unique entity identified by attr and val."
  [db attr val]
  (qe '[:find ?e
        :in $ ?attr ?val
        :where [?e ?attr ?val]]
      db (d/entid db attr) val))

(defn find-entity [db params]
  (let [build-predicate (fn [[k v]] ['?e k v])
        q {:find '[?e]
           :in '[$]
           :where (map build-predicate params)}]
    (map (partial d/entity db) (apply concat (d/q q db)))))

