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
            [ring.middleware.format :refer [wrap-restful-format]]
            [cheshire.core :refer [parse-string generate-string]]))

(load "test_utils")

(r/defresource User
  {:schema {:user/id Int
            :email Str
            :name Str}
   :uniqueness {:user/id :db.unique/identity}})

(def TestUsers
  [{:user/id 1, :email "torvalds@example.com", :name "Linus Torvalds"}
   {:user/id 2, :email "bill@example.com", :name "Bill Gates"}])

(r/defresource Tweet
  {:schema {:tweet/id Int
            :body Str
            :author Int}
   :uniqueness {:tweet/id :db.unique/identity}})

(def TestTweets
  [{:tweet/id 1, :body "first post", :author 2}
   {:tweet/id 2, :body "witty remark", :author 1}
   {:tweet/id 3, :body "snarky joke", :author 1}])


(deftest test-api-get
  (let [api (mock-api-for {:resources [User Tweet]
                           :test-data TestTweets})]
    (let [expected-error "Failed to find Tweet with query: {\"tweet/id\" \"99\"}"
          response (api :get "/tweet" {"tweet/id" 99})]
      (is (= 404            (-> response :status)))
      (is (= expected-error (-> response :body :error))))

    (let [response (api :get "/tweet" {"tweet/id" "1"})]
      (is (= 200            (-> response :status)))
      (is (= 1              (-> response :body :tweet/id)))
  
      (is (= "first post"   (-> response :body :body)))
      (is (= 2              (-> response :body :author))))

    (let [response (api :get "/tweet" {:body "witty remark"})]
      (is (= 200            (:status response)))
      (is (= 2              (-> response :body :tweet/id)))
      (is (= "witty remark" (-> response :body :body)))
      (is (= 1              (-> response :body :author))))))

(deftest test-get-many
  (let [api (mock-api-for {:resources [User Tweet]
                           :test-data TestTweets})]
    (let [response (api :get "/tweet" {:author 1})]
      (is (= 200            (-> response :status)))
      (is (= 2              (count (-> response :body))))
      (is (= '(1 1)         (map :author (-> response :body))))
      (is (= '(2 3)         (sort (map :tweet/id (-> response :body))))))))

(deftest test-api-post
  (let [api (mock-api-for {:resources [User Tweet]
                           :test-data TestTweets})]
    (let [response (api :post "/tweet" {} {:tweet/id 4, :body "test post" :author 1})]
      (is (= 202            (-> response :status)))
      (is (= {}             (-> response :body))))

    (let [response (api :get "/tweet" {"tweet/id" 4})]
      (is (= 200            (-> response :status))))))


