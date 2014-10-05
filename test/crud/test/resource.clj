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
  {:schema {:user/id Int
            :email Str
            :name Str}
   :uniqueness {:user/id :db.unique/identity}})

(def TestUsers [{:user/id 1, :email "torvalds@example.com", :name "Linus Torvalds"}
                {:user/id 2, :email "bill@example.com", :name "Bill Gates"}])

(r/defresource Tweet
  {:schema {:tweet/id Int
            :body Str
            :author (:user/id (:schema User))}
   :uniqueness {:tweet/id :db.unique/identity}})

(def TestTweets [{:tweet/id 1, :body "first post", :author 2}
                 {:tweet/id 2, :body "witty remark", :author 1}
                 {:tweet/id 3, :body "snarky joke", :author 1}])

(deftest test-api-get
  (let [api (mock-api-for {:resources [User Tweet]
                           :test-data TestTweets})
        tweet-not-found {:status 404
                         :body {:error (str "Failed to find Tweet with query: " {"tweet/id" "99"})}}]
    (testing "nonsensical requests"
      (is (= 404 (-> (api :get "/tweet/nonsense") :status)))
      (is (= 422 (-> (api :get "/tweet" {:bad "params"}) :status))))

    (testing "sane requests"
      (testing "not found"
        (is (submap? tweet-not-found (api :get "/tweet" {"tweet/id" 99}))))

      (testing "find by id"
        (is (submap? {:status 200
                      :body {:tweet/id 1
                             :resource "Tweet"
                             :body "first post"
                             :author 2}}
                     (api :get "/tweet" {"tweet/id" "1"})) "find by id"))

      (testing "find by body"
        (is (submap? {:status 200
                      :body {:tweet/id 2
                             :resource "Tweet"
                             :body "witty remark"
                             :author 1}}
                     (api :get "/tweet" {:body "witty remark"}))))

      (testing "find many"
        (let [response (api :get "/tweet" {:author 1})]
          (is (= 200            (-> response :status)))
          (is (= 2              (count (-> response :body))))
          (is (= '(1 1)         (map :author (-> response :body))))
          (is (= '(2 3)         (sort (map :tweet/id (-> response :body))))))))))

(deftest test-api-post
  (let [api (mock-api-for {:resources [User Tweet]
                           :test-data TestTweets})]
    (let [response (api :post "/tweet" {} {:tweet/id 4, :body "test post" :author 1})]
      (is (= 202            (-> response :status)))
      (is (= {}             (-> response :body))))

    (let [response (api :get "/tweet" {"tweet/id" 4})]
      (is (= 200            (-> response :status))))))


