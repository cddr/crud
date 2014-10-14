(ns crud.test.core
  "The aim here is to be the glue between HTTP resources and datomic persistence.
To do that, we provide a function that returns a set of handlers, one for each
HTTP method, that does corresponding thing on an underlying datomic database."
  (:require [clojure.test :refer :all]
            [schema.core :as s :refer [Str Bool Num Int Inst Keyword]]
            [datomic.api :as d]
            [integrity.datomic :as db]
            [crud.core :as r]
            [compojure.core :as c]
            [ring.mock.request :as client]
            [ring.middleware.defaults :refer :all]
            [ring.middleware.format :refer [wrap-restful-format]]
            [liberator.dev :as dev])
  (:import [java.net URI]))

;; TODO: write quick-check generators for these structures to make the code bullet-proof
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

(deftest test-datomic-schema
  (let [{:keys [schema uniqueness refs]} Tweet
        [id body author] (r/datomic-schema schema uniqueness refs)]
    (is (submap? {:db/unique :db.unique/identity, :db/ident :id, :db/valueType :db.type/long} id))
    (is (submap? {:db/ident :body, :db/valueType :db.type/string} body))
    (is (submap? {:db/ident :author, :db/valueType :db.type/ref} author))))

(deftest test-as-response
  (let [schema {:msg Str
                :author Int}
        refs [(r/build-ref User :author :id)]
        entity {:msg "hello world"
                :author {:id 1}}]
    (is (= {:author "user/1", :msg "hello world"} (r/as-response entity schema refs)))))

(deftest test-validator
  (let [schema {:foo Int, :bar Str}
        subject (r/validator schema [:parsed] [:valid] [:error])
        request (fn [method input]
                  (-> (assoc-in {} [:parsed] input)
                      (assoc-in [:request :request-method] method)))
        assert-valid (fn [ctx]
                       (let [[valid? hash] (subject ctx)]
                         (is valid?)
                         (is (contains? hash :valid))))
        assert-invalid (fn [ctx]
                         (let [[valid? hash] (subject ctx)]
                           (is (not valid?))
                           (is (schema.utils/error? (:error hash)))))]
    (testing "full schema validation"
      (assert-invalid (request :post 42))
      (assert-invalid (request :post "a string"))
      (assert-invalid (request :post {:foo "a string", :bar 42}))
      (assert-invalid (request :post {:foo 42}))

      (assert-valid (request :get {:foo 42, :bar "a string"})))

    (testing "optional schema validation"
      (assert-valid (request :get {:foo 42}))
      (assert-valid (request :get {:bar "a string"})))))

(deftest test-malformed?
  (let [subject (r/malformed? [:raw-input] [:parsed-input])
        request (fn [input]
                  (assoc-in {} [:raw-input] input))]
    (is (= [false {:parsed-input 42}] (subject (request (pr-str 42)))))
    (is (= [false {:parsed-input {:name "linus", :email "torvalds@linux.com"}}]
           (subject (request (pr-str {:name "linus", :email "torvalds@linux.com"})))))

    (let [decision (subject (request "{:name}"))]
      (is (= true (first decision)))
      (is (submap? {:parser-error "Map literal must contain an even number of forms"}
                   (second decision))))))

(deftest test-redirector
  (let [redirect (r/redirector [:id])
        request (fn [input]
                  (merge {:request (client/request :post "/thing")}
                         input))]
    (is (= "http://localhost:80/thing/1" (str (redirect (request {:id 1})))))
    (is (= "http://localhost:80/thing/2" (str (redirect (request {:id 2})))))))

(def test-data
  (let [[author] (test-ids 1)]
    [(r/as-facts author {:id 1, :email "linus@linux.com", :name "Linus"}
                 (:refs User))
     (r/as-facts (d/tempid :db.part/user) {:id 2, :body "I'm gonna build an OS", :author author}
                 (:refs Tweet))
     (r/as-facts (d/tempid :db.part/user) {:id 3, :body "Don't send crap like that to me again", :author author}
                 (:refs Tweet))]))
     
  
(deftest test-handler
  (let [{:keys [cnx]} (test-db {:resources [User Tweet]
                                :test-data (vec (reduce concat [] test-data))})]
    (testing "fetch a collection of entities"
      (let [h (r/handler (d/db cnx) :collection Tweet [:query])
            query (fn [params]
                    (h {:query {:author [:id 1]}}))
            result (query {:author [:id 1]})]
        (is (= 2 (count result)))
        (is (every? #{"user/1"} (map :author result)))
        (is (some #{"I'm gonna build an OS"} (map :body result)))))

    (testing "fetch a single entity"
      (let [h (r/handler (d/db cnx) :single User [:query])
            query (fn [params]
                    (h {:query params}))
            result (query {:id 1})]
        (is (= {:name "Linus", :email "linus@linux.com", :id 1}
               result))))))

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
    (is (submap? {:status 404, :body {:error "Could not find tweet with id: 666"}}
                 (api :get "/tweet/666" {} nil)))

    (is (submap? {:status 422, :body {:id '(not (integer? nonsense))}}
                 (api :get "/tweet/nonsense" {} (pr-str {}))))

    (is (submap? {:status 400, :body {:error "EOF while reading"}}
                 (api :put "/tweet/42" {} "{syntax-error")))))

(deftest test-post-then-get
  (let [app (-> (test-app [Tweet User] []))
        api (comp (responder "application/edn")
                  app
                  (requestor "application/edn"))]
    (testing "create with POST"
      (is (submap? {:status 201, :body "Created."}
                   (api :post "/user" {} (pr-str {:id 1, :email "torvalds@linux.com", :name "Linus"})))))

    (testing "create with PUT"
      (is (submap? {:status 201, :body "Created."}
                   (api :put "/user/2" {} (pr-str {:email "hicky@clojure.com", :name "Rich"})))))

    (testing "create with POST and invalid body"
      (is (submap? {:status 422 :body {:id 'missing-required-key}}
                   (api :post "/user" {} (pr-str {:email "torvalds@linux.com", :name "Linus"})))))

    (testing "read with GET"
      (is (submap? {:status 200 :body {:name "Linus", :email "torvalds@linux.com", :id 1}}
                   (api :get "/user/1" {} nil))))

    (testing "create with POST containing reference to other resource"
      (is (submap? {:status 201 :body "Created."}
                   (api :post "/tweet" {} (pr-str {:id 3, :body "Hello World!"
                                                   :author "http://localhost:80/user/1"})))))
    (testing "update resource using PUT"
      (is (submap? {:status 204, :body nil}
                   (api :put "/user/1" {} (pr-str {:email "torvalds@linux.com", :name "Linus Torvalds"}))))
      (is (submap? {:status 200, :body {:id 1, :email "torvalds@linux.com", :name "Linus Torvalds"}}
                   (api :get "/user/1" {} nil)))
      (is (submap? {:status 422, :body {:email 'missing-required-key}}
                   (api :put "/user/1" {} (pr-str {:name "Linus"})))))

    (testing "update resource using PATCH"
      (is (submap? {:status 204, :body nil}
                   (api :patch "/user/1" {} (pr-str {:name "Linus"}))))
      (is (submap? {:status 200, :body {:id 1, :email "torvalds@linux.com", :name "Linus"}}
                   (api :get "/user/1" {} nil))))

    (testing "delete resource"
      (is (submap? {:status 204, :body "Deleted."}
                   (api :delete "/user/2" {} nil)))
      (is (submap? {:status 404}
                   (api :get "/user/2" {} nil))))))

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
