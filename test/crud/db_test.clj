(ns crud.db-test
  (:require [clojure.test :refer :all]
            [crud.resource :refer :all]
            [crud.test-entities :refer :all]
            [crud.db :refer :all]
            [environ.core :refer [env]]))

(deftest test-datomic-init
  (testing "creates attributes for entities"
    (let [db (datomic-db :uri (env :crud-db-uri)
                         :entities [User])]
      (doseq [attr [:id :email :name :secret]]
        (is (has-attr? db attr))))))

;  (testing "saves entity values"
    


    
