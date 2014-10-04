(ns crud.test.resource
  "The aim here is to be the glue between HTTP resources and datomic persistence.
To do that, we provide a function that returns a set of handlers, one for each
HTTP method, that does corresponding thing on an underlying datomic database."
  (:require [clojure.test :refer :all]
            [schema.core :as s :refer [Str Bool Num Int Inst Keyword]]
            [datomic.api :as d]
            [integrity.datomic :as db]
            [crud.resource :as r]
            [ring.mock.request :as client]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [cheshire.core :refer [parse-string generate-string]]))

;; This is what defines our API in it's entirety. All users of this library
;; need to do is 
(r/defresource Tweet
  {:schema {:id Int
            :body Str
            :author {:name Str}}
   :uniqueness {:id :db.unique/identity}})

(def TestTweets
  [{:id 1, :body "first post",
    :author {:name "cddr"}}
   {:id 2, :body "witty remark",
    :author {:name "cddr"}}
   {:id 3, :body "witty remark",
    :author {:name "copy cat"}}])

(defn dbg-handler [handler]
  (fn [req]
    (prn "req: " req)
    (let [resp (handler req)]
      (prn "resp: " resp)
      resp)))

(defn make-test-api [resource]
  (fn [cx]
    (fn test-api
      ([method params]
         (test-api method params {}))
      ([method params body]
         (let [app (-> (r/api-routes cx resource)
                       (wrap-defaults api-defaults)
                       (wrap-json-body {:keywords? true})
                       wrap-json-response)
               parse-response (fn [response]
                                (assoc response :body (parse-string (:body response) true)))]
           (-> (ring.mock.request/request method "/" params)
               (ring.mock.request/content-type "application/json")
               (ring.mock.request/body (generate-string body))
               app
               parse-response))))))

(def test-uri "datomic:mem://test-db")

(defn test-setup []
  (d/create-database test-uri)
  (d/connect test-uri))

(defn test-teardown []
  (d/delete-database test-uri))

(defn api-testing [test-env test-fn]
  (let [{:keys [resource test-data]} test-env
        {:keys [schema uniqueness name]} resource
        tx (fn [cx tx-data]
             @(d/transact cx tx-data)
             cx)
        api (make-test-api resource)]
    (let [test-result (test-fn (-> (test-setup)
                                   (tx (db/attributes schema uniqueness))
                                   (tx (r/datomic-facts test-data))
                                   api))]
      (test-teardown)
      test-result)))


(deftest test-api-get
  (api-testing {:resource Tweet
                :test-data TestTweets}
    (fn [api]
      (let [response (api :get {:id "999"})]
        (is (= 404          (-> response :status)))
        (is (= "Failed to find Tweet with query: {:id \"999\"}"
                            (-> response :body :error))))

      (let [response (api :get {:id "1"})]
        (is (= 200          (-> response :status)))
        (is (= 1            (-> response :body :id)))
        (is (= "first post" (-> response :body :body)))
        (is (= "cddr"       (-> response :body :author :name))))

      (let [response (api :get {:body "witty remark"})]
        (is (= 200 (:status response)))
        (is (= 2              (-> response :body :id)))
        (is (= "witty remark" (-> response :body :body)))))))

(deftest test-api-post
  (api-testing {:resource Tweet
                :test-data TestTweets}
    (fn [api]
      (let [response (api :post {} {:id 4, :body "test post", :author {:name "cddr"}})]
        (is (= 202 (-> response :status)))
        (is (= {} (-> response :body)))))))


