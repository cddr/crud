(ns crud.hypercrud-test
  (:require
   [clojure.test :refer :all]
   [crud.hypercrud :refer :all]
   [crud.entity :refer :all]
   [crud.db :refer [crud-db commit!]]
   [crud.db.datomic]
   [crud.hyperclient :refer :all]
   [datomic.api :as d]
   [schema.core :as s :refer [Str Num Inst Int Bool Keyword]]
   [environ.core :refer [env]]
   [ring.mock.request :as mock]))

(defentity Department
  :schema {:id Int
           :name Str}
  :uniqueness {:id :db.unique/identity})

(defentity Employee
  :schema {:name Str}
  :links [(link :department [Department :id])])

(defentity Cart
  :schema {:id Int
           :name Str
           :items [{:id Int
                    :qty Int
                    :price Num}]})

(defn test-fixture [entities]
  (let [db (do
             (d/delete-database (env :crud-db-uri))
             (crud-db {:type :datomic, :uri (env :crud-db-uri),
                       :entities entities}))]
    {:db db
     :server (hypercrud {:db db, :entities entities})}))

(deftest test-linked-entities
  (testing "with employee/department fixtures"
    (let [{:keys [db server]} (test-fixture [Department Employee])
          dept-client (hyperclient "/Department" server)
          emp-client (hyperclient "/Employee" server)]
      (let [payroll (do (follow dept-client (->> (links dept-client)
                                                 (filter (rel= "create"))
                                                 first)
                                {:method :post
                                 :body {:id 1, :name "payroll"}}))]
        (current dept-client)))
            

        ;;                 (when (= 201 (status dept-client))
        ;;                   (location dept-client)))]
        ;; (is (not (nil? payroll)))

        ;; (let [john (do (follow emp-client (->> (links emp-client)
        ;;                                        (filter (rel= "create"))
        ;;                                        first)
        ;;                        {:method :post
        ;;                         :body {:id 2, :name "john"
        ;;                                :_links [{:rel "department"
        ;;                                          :href payroll}]}})
        ;;                (current emp-client))]
        ;;   john)))

;;         (current dept-client)))
        ;;                 )]
        ;; payroll))
            
        ;;     john ]
        ;; john))
                     
        

;;       (follow emp-client (->> (links client)
;;                               (
;;       (follow-redirect client)
;;       (follow-collection client)
;;       (current client))

(deftest test-create-with-component
  (testing "with cart fixture"
    (let [{:keys [db server]} (test-fixture [Cart])
          client (hyperclient "/Cart" server)]

      (testing "create cart"
        (follow client (->> (links client)
                            (filter (rel= "create"))
                            first)
                {:method :post
                 :body {:id 1, :name "x-mas list"
                        :items [{:id 2, :qty 10, :price 99.99}
                                {:id 3, :qty 1, :price 50.0}]}})
        (is (= 201 (status client)))))))

(deftest test-hypermedia
  (testing "collection resource"
    (let [{:keys [db server]} (test-fixture [Department])
          client (hyperclient "/Department" server)]

      (testing "presented with operations"
        (is (= 200 (status client)))
        (is (= #{"self" "create"}
               (set (map :rel (links client))))))

      (testing "create"
        (follow client (->> (links client)
                            (filter (rel= "create"))
                            first)
                {:method :post
                 :body {:id 1, :name "drivers"}})
        (is (= 201 (status client))))
      
      (testing "redirected after create"
        (follow-redirect client)
        (is (= 200 (status client)))
        (is (= #{"self" "collection"}
               (set (map :rel (links client)))))
        (is (= {:id 1, :name "drivers"}
               (dissoc (body client) :_links)))
        (follow-collection client))

      (let [item? (rel= "item")]
        (testing "index"
          (is (= 1 (count (filter item? (links client))))))

        (testing "destroy"
          (follow client (->> (links client)
                              (filter item?)
                              first)
                  {:method :delete, :body nil})
          ;; Respond to DELETE requests by returning the deleted resource's collection
          (is (= 200 (status client)))
          (is (= 0 (count (filter item? (links client))))))))))

  
            
  
