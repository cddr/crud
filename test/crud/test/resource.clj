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
            :author Str}
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
    (is (= [:id 42] ((:as-lookup-ref test-ref) "user/42")))
    (is (= [:id 42] ((:as-lookup-ref test-ref) "http://example.com/user/42")))

    ;; At the back of my head here is that in reality, ids are going to be squuids so
    ;; there should just be a single "id" attribute would typically be shared between
    ;; all entities.
    (is (= [:id 42] ((:as-lookup-ref test-ref) "http://example.com/nested/user/42")))))

(deftest test-known-content-type?
  (let [mime-request (fn [mime]
                       {:request (-> (client/request :get "/yolo")
                                     (client/content-type mime))})]
    (is (r/known-content-type? (mime-request "application/edn")))
    (is (= [false {:error "Unsupported content type"}]
           (r/known-content-type? (mime-request "application/json"))))))

(defn requestor [mime-type]
  (fn [method path params body]
    (-> (client/request method path body)
        (client/content-type mime-type)
        (client/header "Accept" mime-type)
        (client/body body))))

(defn responder [mimetype]
  (fn [response]
    (case mimetype
      "application/edn" (assoc response
                          :body (clojure.edn/read-string (:body response))))))
  
(deftest test-garbage-requests
  (let [app (-> (test-app [Tweet] []))
        api (comp (responder "application/edn")
                  app
                  (requestor "application/edn"))]
    (is (submap? {:status 404, :body {:error "Could not find tweet with id: nonsense"}}
                 (api :get "/tweet/nonsense" {} (pr-str {}))))

    (is (submap? {:status 400, :body {:error "EOF while reading"}}
                 (api :put "/tweet/42" {} "{syntax-error")))))

(deftest test-post-then-get
  (let [app (-> (test-app [Tweet User] []))
        api (comp (responder "application/edn")
                  app
                  (requestor "application/edn"))]
    (testing "creation"
      (is (submap? {:status 201, :body "Created."}
                   (api :post "/user" {} (pr-str {:id 1, :email "torvalds@linux.com", :name "Linus"})))))

    (testing "creation with validation errors"
      (is (submap? {:status 422 :body {:id 'missing-required-key}}
                   (api :post "/user" {} (pr-str {:email "torvalds@linux.com", :name "Linus"})))))

    (testing "get previously created resource"
      (is (submap? {:status 200 :body {:name "Linus", :email "torvalds@linux.com", :id 1}}
                   (api :get "/user/1" {} nil))))

    (testing "create resource that references previously created resource"
      (is (submap? {:status 201 :body "Created."}
                   (api :post "/tweet" {} (pr-str {:id 3, :body "Hello World!"
                                                   :author "http://localhost:80/user/1"})))))))
                

;; (deftest test-patch
;;   (let [api (mock-api-for {:resources [Tweet User]})]
;;     (api :post "/user" {} {:id 1, :email "torvalds@linux.com", :name "Linus"})
;;     (let [response (api :post "/tweet" {} {:body "crappy tweet", :author 1})
;;           posted-uri (get-in response [:headers "Location"])]
;;       (api :patch posted-uri {} {:body "better tweet"})

;;       (is (submap? {:body "better tweet"
;;                     :author 1}
;;                    (:body (api :get posted-uri)))))))

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
