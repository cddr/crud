(ns crud.db-test
  (:require [clojure.test :refer :all]
            [crud.resource :refer :all]
            [crud.test-entities :refer :all]
            [crud.db :refer :all]
            [datomic.api :as d]
            [environ.core :refer [env]]))

(deftest test-datomic-init
  (testing "creates attributes for entities"
    (let [db (datomic-db :uri (env :crud-db-uri)
                         :entities [User])]
      (doseq [attr [:id :email :name :secret]]
        (is (has-attr? db attr))))))

(deftest test-make-fact
  (let [id (d/tempid :db.part/user)
        user (partial make-fact Tweet id)]

    ;; standard key/value just spliced into a datomic datom
    (is (= [[:db/add id :body "hello world"]]
           (user :body "hello world")))

    ;; for refs, we generate datomic lookup-refs
    (is (= [[:db/add id :author [:id 42]]]
           (user :author "/author/42")))))



;  (testing "saves entity values"
    


    
