(ns crud.db-test
  (:require [clojure.test :refer :all]
            [crud.entity :refer :all]
            [schema.core :as s :refer [Str Num Inst Int Bool Keyword]]
            [crud.db :refer [crud-db has-attr?]]
            [crud.db.datomic :refer [facts-for entity-attributes]]
            [datomic.api :as d]
            [environ.core :refer [env]]
            [clojure.data :refer [diff]]))

(defentity Department
  :schema {:id Int
           :name Str}
  :uniqueness {:id :db.unique/identity})

(defentity User
  :schema {:id Int
           :role Str
           :email Str
           :name Str}
  :uniqueness {:id :db.unique/identity}
  :links [(link :department [Department :id])])

(defentity Cart
  :schema {:id Int
           :items [{:id Int
                    :qty Int
                    :price Num}]})

(defn test-db [entities]
  (d/delete-database (env :crud-db-uri))
  (crud-db {:type :datomic, :uri (env :crud-db-uri), :entities entities}))

(deftest test-entity-attributes
  (testing "builtins"
    (let [db (test-db [])]
      (is (has-attr? db :entity))))

  (testing "simple attributes"
    (let [db (test-db [Department])]
      (is (has-attr? db :id))
      (is (has-attr? db :name))))

  (testing "nested attributes"
    (let [db (test-db [Cart])]
      (is (has-attr? db :qty))
      (is (has-attr? db :price))))

  (testing "linked attributes"
    (let [db (test-db [User])]
      (is (has-attr? db :department)))))

;; (deftest test-transactions
;;   (testing "commit!"
;;     (let [db (test-db [Department])
;;           show (fn [id] (represent db Department (find-by-id db id)))]
;;       (commit! db Department {:id 1 :name "foo"})
;;       (= {:id 1, :name "foo"}
;;          (show 1)))
;;       ;; (is (= "foo"
;;       ;;        (:name (find-by-id db 1))))))

;;   (testing "commit with link"
;;     (let [db (test-db [Department User])]
;;       (commit! db Department {:id 1, :name "drivers"})
;;       (commit! db User {:id 2, :name "linus",
;;                         :links {:department 1}})

;;       (let [linus (find-by-id db 2)]
;;         (is (= "linus"
;;                (:name linus)))
;;         (is (= "drivers"
;;                ((comp :name :department) linus))))))

;;   (testing "commit with nested attributes"
;;     (let [db (test-db [Cart])]
;;       (commit! db Cart {:id 1
;;                         :items [{:id 2, :qty 10, :price 9.99}]})

;;       ;; (is (= {:id 1
;;       ;;         :items [{:id 2, :qty 10, :price 9.99}]}
;;       ;; (diff {:id 1
;;       ;;        :items #{{:id 2, :qty 10, :price 9.99}}}
;; ;            (d/touch (find-by-id db 1)))

;;       (let [{:keys [id items]} 
;;         (is (= 1 id))

;;         (let [{:keys [id qty price]} (first items)]
;;           (is (= 10 qty))
;;           (is (= 9.99 price))))))

        
;;         store (partial commit! db Department)
;;         retract #(retract! db Department (first (find-by-id db %)))
;;         retrieve #(into {} (first (find-by-id db %)))]

;;     (testing "can commit! entity"
;;       (store {:id 1 :name "foo"})
;;       (store {:id 2 :name "bar"})

;;       (is (= {:id 1 :name "foo"} (retrieve 1)))
;;       (is (= {:id 2 :name "bar"} (retrieve 2))))

;;     (testing "can retract! entity"
;;      (retract 1)
;;      (is (empty? (retrieve 1))))))

;; (deftest test-representation
;;   (let [db (test-db [Department User])
;;         dept! (partial commit! db Department)
;;         user! (partial commit! db User)
;;         dept? #(represent db Department (first (find-by-id db %)))
;;         user? #(represent db User (first (find-by-id db %)))]
    
;;     (testing "basic representation"
;;       (dept! {:id 1 :name "foo"})
;;       (is (= {:id 1, :name "foo"} (dept? 1))))))

    ;; (testing "link representation"
    ;;   (user! {:id 2 :email "linus@example.com"
    ;;           :_links {:department 1}})))

;;       (is (= [[:db/add 0 :id 1]
;;               [:db/add 0 :department [:department 1]]]
;;              ((facts-for db User {:id 1, :_links {:department 1}}) 0))))))
    
    
      
