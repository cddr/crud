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
            [ring.middleware.format :refer [wrap-restful-format]]))

(load "test_utils")

(r/defresource User
  {:schema {:id Int
            :email Str
            :name Str}
   :uniqueness {:id :db.unique/identity}})

(r/defresource Tweet
  {:schema {:id Int
            :body Str
            :author (:id (:schema User))}
   :uniqueness {:id :db.unique/identity}})

(deftest test-garbage-requests
  (let [api (mock-api-for {:resources [Tweet]})]
    (is (= 404 (-> (api :get "/tweet/nonsense") :status)))
    (is (= 422 (-> (api :get "/tweet" {:bad "params"}) :status)))))

(deftest test-not-found
  (let [api (mock-api-for {:resources [Tweet]})]
    (is (submap? {:status 404
                  :body {:error "Failed to find Tweet with id: 99"}}
                 (api :get "/tweet/99")))))

(deftest test-post
  (let [api (mock-api-for {:resources [Tweet]})]
    (let [response (api :post "/tweet" {} {:id 4, :body "test post" :author 1})]
      (is (= 202            (-> response :status)))
      (is (= {}             (-> response :body))))))

(deftest test-find-by-id
  (let [api (mock-api-for {:resources [Tweet]})]
    (api :post "/tweet" {} {:id 101, :author 1, :body "hello world!"})

    (is (submap? {:status 200
                  :body {:id 101
                         :body "hello world!"
                         :author 1}}
                 (api :get "/tweet/101")))))

(deftest test-find-by-attr
  (let [api (mock-api-for {:resources [Tweet]})]
    (api :post "/tweet" {} {:id 101, :author 1, :body "hello world!"})

    (is (submap? {:status 200
                  :body {:id 101
                         :body "hello world!"
                         :author 1}}
                 (api :get "/tweet" {:body "hello world!"})))))

(deftest test-find-many
  (let [api (mock-api-for {:resources [Tweet]})]
    (api :post "/tweet" {} {:id 101, :author 1, :body "hello world!"})
    (api :post "/tweet" {} {:id 102, :author 1, :body "writing an OS, brb"})

    (let [response (api :get "/tweet" {:author 1})]
      (is (= 200            (-> response :status)))
      (is (= 2              (count (-> response :body))))
      (is (= '(1 1)         (map :author (-> response :body))))
      (is (= '(101 102)     (sort (map :id (-> response :body))))))))
