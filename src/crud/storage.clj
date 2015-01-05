(ns crud.storage
  "The storage namespace aims to provide a means by which developers can specify which
attributes of an entity are persisted to the DB and optionally, how they should be
transformed before doing so."
  )

(defn storage-name [storable]
  (if (keyword? storable)
    storable
    (:name storable)))

(defn storage-value [storable value]
  (if (keyword? storable)
    value
    ((:callable storable) value)))

(defn storable? [entity k]
  (some #{k} (map storage-name (:storable entity))))

;; (defn as-stored [entity input]
;;   (let [reducer (fn [m [k v]]
;;                   (if (contains? object k)
;;                     (assoc m k v)
;;                     m))
;;         reader (fn [attr]
;;                  (if (map? attr)
;;                    [(:name attr) ((:callable attr) ((:name attr) object))]
;;                    [attr (attr object)]))]
;;     (reduce reducer {} (map reader (:storables entity)))))
