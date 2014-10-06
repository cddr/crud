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

(def TestUsers [{:id 1, :email "torvalds@example.com", :name "Linus Torvalds"}
                {:id 2, :email "bill@example.com", :name "Bill Gates"}])

(r/defresource Tweet
  {:schema {:id Int
            :body Str
            :author (:id (:schema User))}
   :uniqueness {:id :db.unique/identity}})

(def TestTweets [{:id 11, :body "first post", :author 2}
                 {:id 12, :body "witty remark", :author 1}
                 {:id 13, :body "snarky joke", :author 1}])

(deftest test-api-get
  (let [api (mock-api-for {:resources [User Tweet]
                           :test-data TestTweets})
        tweet-not-found {:status 404
                         :body {:error "Failed to find Tweet with id: 99"}}]
    (testing "nonsensical requests"
      (is (= 404 (-> (api :get "/tweet/nonsense") :status)))
      (is (= 422 (-> (api :get "/tweet" {:bad "params"}) :status))))

    (testing "sane requests"
      (testing "find nothing"
        (is (submap? tweet-not-found (api :get "/tweet/99"))))

      (testing "find by id"
        (is (submap? {:status 200
                      :body {:id 11
                             :resource "Tweet"
                             :body "first post"
                             :author 2}}
                     (api :get "/tweet/11"))))

      (testing "find by attribute"
        (is (submap? {:status 200
                      :body {:id 12
                             :resource "Tweet"
                             :body "witty remark"
                             :author 1}}
                     (api :get "/tweet" {:body "witty remark"}))))

      (testing "find many"
        (let [response (api :get "/tweet" {:author 1})]
          (is (= 200            (-> response :status)))
          (is (= 2              (count (-> response :body))))
          (is (= '(1 1)         (map :author (-> response :body))))
          (is (= '(12 13)       (sort (map :id (-> response :body))))))))))

(deftest test-api-post
  (let [api (mock-api-for {:resources [User Tweet]
                           :test-data TestTweets})]
    (let [response (api :post "/tweet" {} {:id 4, :body "test post" :author 1})]
      (is (= 202            (-> response :status)))
      (is (= {}             (-> response :body))))

    (let [response (api :get "/tweet" {:id 4})]
      (is (= 200            (-> response :status))))))


