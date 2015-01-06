(ns crud.entity)

(defn referrer [entity attribute]
  (let [{refs :refs} entity
        matched (fn [ref]
                  (if (= (:referrer ref))
                    ref))]
    (some matched refs)))

         
