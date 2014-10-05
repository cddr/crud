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

(defn path-prefix [resource] (str "/" (clojure.string/lower-case (:name resource))))

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

(defn dbg-handler [handler msg]
  (fn [req]
    (prn "req: " msg req)
    (let [resp (handler req)]
      (prn "resp: " resp)
      resp)))

(def test-uri "datomic:mem://test-db")

(defn test-setup []
  (d/create-database test-uri)
  (d/connect test-uri))

(defn test-teardown []
  (d/delete-database test-uri))

(defn mock-api [cx resources & request-args]
  (let [app (-> (apply c/routes (for [r resources]
                                  (c/context (path-prefix r) []
                                    (r/api-routes cx r))))
                (wrap-restful-format :formats [:edn :json])
                (wrap-defaults api-defaults))
        parse-response (fn [response]
                         (assoc response :body (clojure.edn/read-string (slurp (:body response)))))]
    (let [[method path params body] request-args]
      (-> (ring.mock.request/request method path params)
          (ring.mock.request/content-type "application/edn")
          (ring.mock.request/body (str (or body {})))
          app
          parse-response))))

(defn mock-api-for [test-env]
  (let [{:keys [resources test-data]} test-env
        keep-one-ident (fn [acc next]
                         (if (some #(= (:db/ident %)
                                       (:db/ident next)) acc)
                           acc
                           (conj acc next)))
        tx (fn [cx tx-data]
             @(d/transact cx tx-data)
             cx)
        meta (reduce keep-one-ident [] (into [] (mapcat db/attributes (map :schema resources)
                                                     (map :uniqueness resources))))
        data (r/datomic-facts test-data)]
    (partial mock-api (-> (test-setup) (tx meta) (tx data)) resources)))

(deftest test-api-get
  (let [api (mock-api-for {:resources [User Tweet]
                           :test-data TestTweets})]
    (let [response (api :get "/tweet" {"tweet/id" 99})]
      (is (= 404          (-> response :status)))
      (is (= "Failed to find Tweet with query: {\"tweet/id\" \"99\"}"
             (-> response :body :error))))

    (let [response (api :get "/tweet" {"tweet/id" "1"})]
      (is (= 200          (-> response :status)))
      (is (= 1            (-> response :body :tweet/id)))
  
      (is (= "first post" (-> response :body :body)))
      (is (= 2            (-> response :body :author))))

    (let [response (api :get "/tweet" {:body "witty remark"})]
      (is (= 200 (:status response)))
      (is (= 2              (-> response :body :tweet/id)))
      (is (= "witty remark" (-> response :body :body)))
      (is (= 1              (-> response :body :author))))))

(deftest test-get-many
  (let [api (mock-api-for {:resources [User Tweet]
                           :test-data TestTweets})]
    (let [response (api :get "/tweet" {:author 1})]
      (is (= 200 (-> response :status)))
      (is (= 2 (count (-> response :body))))
      (is (= '(1 1) (map :author (-> response :body))))
      (is (= '(2 3) (sort (map :tweet/id (-> response :body))))))))

(deftest test-api-post
  (let [api (mock-api-for {:resources [User Tweet]
                           :test-data TestTweets})]
    (let [response (api :post "/tweet" {} {:tweet/id 4, :body "test post" :author 1})]
      (is (= 202 (-> response :status)))
      (is (= {} (-> response :body))))

    (let [response (api :get "/tweet" {"tweet/id" 4})]
      (is (= 200 (-> response :status))))))


