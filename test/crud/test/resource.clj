(ns crud.test.resource
  "The aim here is to be the glue between HTTP resources and datomic persistence.
To do that, we provide a function that returns a set of handlers, one for each
HTTP method, that does corresponding thing on an underlying datomic database."
  (:require [clojure.test :refer :all]
            [schema.core :as s :refer [Str Bool Num Int Inst Keyword]]
            [datomic.api :as d]
            [integrity.datomic :as db]
            [crud.resource :as r]
            [compojure.core :as c]
            [ring.mock.request :as client]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.format :refer [wrap-restful-format]])
  (:import [java.net URI]))

(def User
  {:name "user"
   :schema {:id Int
            :email Str
            :name Str}
   :uniqueness {:id :db.unique/identity}})

(def Tweet
  {:name "tweet"
   :schema {:id Int
            :body Str
            :author URI}
   :uniqueness {:id :db.unique/identity}
   :refs [(r/build-ref User :author :id)]})

(load "test_utils")
(load "datomic")
(load "protocol")

(deftest test-get?
  (is (= true (r/get? {:request (client/request :get "/yolo")})))
  (is (= false (r/get? {:request (client/request :post "/yolo")}))))

(deftest test-build-ref
  (let [test-ref (r/build-ref User :author :id)]
    ;; Represents a situation where the :author attr is a reference to the :id attr of the User entity
    (is (= User (:resource test-ref)))    
    (is (= :author (:referrer test-ref)))
    (is (= :id (:referent test-ref)))
    
    (is (= "user/42" ((:as-response test-ref) {:author {:id 42}})))
    (is (= [:id 42] ((:as-lookup-ref test-ref) "user/42")))))

(deftest test-known-content-type?
  (let [mime-request (fn [mime]
                       {:request (-> (client/request :get "/yolo")
                                     (client/content-type mime))})]
    (is (r/known-content-type? (mime-request "application/edn")))
    (is (= [false {:error "Unsupported content type"}]
           (r/known-content-type? (mime-request "application/json"))))))

  
;; (deftest test-garbage-requests
;;   (let [api (mock-api-for {:resources [Tweet]})]
;;     (is (= 404 (-> (api :get "/tweet/nonsense") :status)))
;;     (is (= 422 (-> (api :get "/tweet" {:bad "params"}) :status)))))

;; (deftest test-not-found
;;   (let [api (mock-api-for {:resources [Tweet]})]
;;     (is (submap? {:status 404
;;                   :body {:error "Failed to find Tweet with id: 99"}}
;;                  (api :get "/tweet/99")))))

;; (deftest test-post
;;   (let [api (mock-api-for {:resources [Tweet User]})]
;;     (api :post "/user" {} {:id 1, :email "torvalds@linux.com", :name "Linus"}) 
;;     (let [response (api :post "/tweet" {} {:body "test post" :author 1})]
;;       (is (= 202            (-> response :status)))
;;       (is (= {}             (-> response :body))))))

;; (deftest test-patch
;;   (let [api (mock-api-for {:resources [Tweet User]})]
;;     (api :post "/user" {} {:id 1, :email "torvalds@linux.com", :name "Linus"})
;;     (let [response (api :post "/tweet" {} {:body "crappy tweet", :author 1})
;;           posted-uri (get-in response [:headers "Location"])]
;;       (api :patch posted-uri {} {:body "better tweet"})

;;       (is (submap? {:body "better tweet"
;;                     :author 1}
;;                    (:body (api :get posted-uri)))))))

;; (deftest test-find-by-id
;;   (let [api (mock-api-for {:resources [Tweet User]})]
;;     (api :post "/user" {} {:id 1, :email "torvalds@linux.com", :name "Linus"})    
;;     (api :post "/tweet" {} {:id 101, :author 1, :body "hello world!"})

;;     (is (submap? {:status 200
;;                   :body {:id 101
;;                          :body "hello world!"
;;                          :author 1}}
;;                  (api :get "/tweet/101")))))

;; (deftest test-find-by-attr
;;   (let [api (mock-api-for {:resources [Tweet User]})]
;;     (api :post "/user" {} {:id 1, :email "torvalds@linux.com", :name "Linus"})    
;;     (api :post "/tweet" {} {:id 101, :author 1, :body "hello world!"})

;;     (is (submap? {:status 200
;;                   :body {:id 101
;;                          :body "hello world!"
;;                          :author 1}}
;;                  (api :get "/tweet" {:body "hello world!"})))))

;; (deftest test-find-many
;;   (let [api (mock-api-for {:resources [Tweet User]})]
;;     (api :post "/user" {} {:id 1, :email "torvalds@linux.com", :name "Linus"})    
;;     (api :post "/tweet" {} {:id 101, :author 1, :body "hello world!"})
;;     (api :post "/tweet" {} {:id 102, :author 1, :body "writing an OS, brb"})

;;     (let [response (api :get "/tweet" {:author 1})]
;;       (is (= 200            (-> response :status)))
;;       (is (= 2              (count (-> response :body))))
;;       (is (= '(1 1)         (map :author (-> response :body))))
;;       (is (= '(101 102)     (sort (map :id (-> response :body))))))))
