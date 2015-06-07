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
  :schema {:name Str})

(defentity Employee
  :schema {:name Str}
  :links [(link :department [Department :id])])

(defentity Cart
  :schema {:name Str
           :items [{:qty Int
                    :price Num}]})

(defn test-fixture [entities]
  (let [db (do
             (d/delete-database (env :crud-db-uri))
             (crud-db {:type :datomic, :uri (env :crud-db-uri),
                       :entities entities}))]
    {:db db
     :server (hypercrud {:db db, :entities entities})}))

(defn create [client params]
  (follow client (get-in (body client) [:_links :create])
          {:method :post
           :body params})
  (location client))

(deftest test-create-with-link
  (testing "link department to employee"
    (let [{:keys [db server]} (test-fixture [Employee Department])
          dept (hyperclient "/Department" server)
          emp (hyperclient "/Employee" server)]
      (let [sales (create dept {:id 1, :name "sales"})]
        (create emp {:id 2, :name "andy"
                     :_links {:department {:href sales}}})
        (follow-redirect emp)
        (is (= {:href sales}
               (get-in (body emp) [:_links :department])))))))

(deftest test-create-with-component
  (testing "with cart fixture"
    (let [{:keys [db server]} (test-fixture [Cart])
          client (hyperclient "/Cart" server)]

      (testing "create cart"
        (follow client (get-in (body client) [:_links :create])
                {:method :post
                 :body {:id 1, :name "x-mas list"
                        :items [{:qty 10, :price 99.99}
                                {:qty 1, :price 50.0}]}})
        (is (= 201 (status client)))))))

(deftest test-hypermedia
  (testing "collection resource"
    (let [{:keys [db server]} (test-fixture [Department])
          client (hyperclient "/Department" server)]

      (testing "presented with operations"
        (is (= 200 (status client)))
        (is (= #{:self :create :item}
               (set (keys (links client))))))

      (testing "create"
        (follow client (get-in (body client) [:_links :create])
                {:method :post
                 :body {:id 1, :name "drivers"}})
        (is (= 201 (status client))))
      
      (testing "redirected after create"
        (follow-redirect client)
        (is (= 200 (status client)))
        (is (= #{:self :collection}
               (set (keys (links client)))))
        (is (= {:id 1, :name "drivers"}
               (dissoc (body client) :_links))))

      (testing "index"
        (follow-collection client)
        (is (= 1 (count (get-in (body client) [:_links :item])))))
    
      (testing "destroy"
        (follow client (-> (get-in (body client) [:_links :item])
                           first)
                {:method :delete, :body nil})
          ;; Respond to DELETE requests by returning the deleted resource's collection
        (is (= 200 (status client)))
        (is (= 0 (count (get-in (body client) [:_links :item]))))))))

  
            
  
