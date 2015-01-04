(ns crud.test-mocks
  (:require [crud.resource :refer :all]))

(defmethod as-response :test [resource]
  (:value resource))

(defn mock-resource [name & {:keys [schema]}]
  (fn [value]
    {:db :test
     :entity {:name name
              :schema schema}
     :value value}))
