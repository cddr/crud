(ns crud.core-test
  "The aim here is to be the glue between HTTP resources and datomic persistence.
To do that, we provide a function that returns a set of handlers, one for each
HTTP method, that does corresponding thing on an underlying datomic database."
  (:require [clojure.test :refer :all]
            [schema.core :as s :refer [Str Bool Num Int Inst Keyword]]
            [datomic.api :as d]
            [integrity.datomic :as db]
            [crud.core :as r]
            [crud.test-entities :refer :all]
            [compojure.core :as c]
            [ring.mock.request :as client]
            [ring.middleware.defaults :refer :all]
            [crypto.password.bcrypt :as password]
            [environ.core :refer [env]])
  (:import [java.net URI]))

(load "test_utils")

(deftest test-datomic-schema
  (let [{:keys [schema uniqueness refs storable]} Tweet
        [id body author] (r/datomic-schema schema uniqueness refs storable)]
    (is (submap? {:db/unique :db.unique/identity, :db/ident :id, :db/valueType :db.type/long} id))
    (is (submap? {:db/ident :body, :db/valueType :db.type/string} body))
    (is (submap? {:db/ident :author, :db/valueType :db.type/ref} author)))

  (let [{:keys [schema uniqueness refs storable]} StorageTest]
    (is (= 2 (count (r/datomic-schema schema uniqueness refs storable))))))

(deftest test-as-response
  (let [schema {:msg Str
                :author Int}
        refs [(r/build-ref User :author :id)]
        entity {:msg "hello world", :author {:id 1}}]
    (is (= {:author "user/1", :msg "hello world"} (r/as-response entity schema refs)))))

(def test-data
  (let [[author] (test-ids 1)]
    [(r/as-facts author {:id 1, :email "linus@linux.com", :name "Linus", :secret "i can divide by 0"}
                 (:refs User))
     (r/as-facts (d/tempid :db.part/user) {:id 2, :body "I'm gonna build an OS", :author author}
                 (:refs Tweet))
     (r/as-facts (d/tempid :db.part/user) {:id 3, :body "Don't send crap like that to me again", :author author}
                 (:refs Tweet))]))

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
  (let [app (-> (r/crud-app [Tweet] []))
        api (comp (responder "application/edn")
                  app
                  (requestor "application/edn"))]

    (let [response (api :get "/tweet/666" {} nil)]
      (is (not-found? response))
      (is (error? response "Could not find tweet with id: 666")))

    (let [response (api :get "/tweet/nonsense" {} (pr-str {}))]
      (is (not-found? response))
      (is (error? response "Could not find tweet with id: nonsense")))

    (let [response (api :put "/tweet/42" {} "{syntax-error")]
      (is (bad-request? response))
      (is (error? response "EOF while reading")))))

(deftest test-api-with-writer
  (let [app (-> (r/crud-app [User] []))
        api (comp (responder "application/edn")
                  app
                  (requestor "application/edn"))]
    (testing "create with writer"
      (api :post "/user" {} (pr-str {:id 1, :email "torvalds@linux.com",
                                     :name "Linus", :secret "yolo"}))
      (let [response (api :get "/user/1" {} nil)]
        (is (ok? response))
        (let [secret (get-in response [:body :secret])]
          (is (password/check "yolo" secret)))))))

(deftest test-basic-api
  (let [app (-> (r/crud-app [Tweet User] []))
        api (comp (responder "application/edn")
                  app
                  (requestor "application/edn"))]
    (testing "create with POST"
      (is (submap? {:status 201, :body "Created."}
                   (api :post "/user" {} (pr-str {:id 1, :email "torvalds@linux.com",
                                                  :name "Linus", :secret "i can divide by zero"})))))

    (testing "create with PUT"
      (is (submap? {:status 201, :body "Created."}
                   (api :put "/user/2" {} (pr-str {:email "hicky@clojure.com", :name "Rich",
                                                   :secret "decomplect ftw"})))))

    (testing "create with POST and invalid body"
      (is (submap? {:status 422 :body {:id 'missing-required-key :secret 'missing-required-key}}
                   (api :post "/user" {} (pr-str {:email "torvalds@linux.com", :name "Linus"})))))

    (testing "read with GET"
      (let [response (api :get "/user/1" {} nil)]
        (is (= 200 (:status response)))
        (is (submap? {:name "Linus", :email "torvalds@linux.com", :id 1}
                     (:body response)))))

    (testing "create with POST containing reference to other resource"
      (is (submap? {:status 201 :body "Created."}
                   (api :post "/tweet" {} (pr-str {:id 3, :body "Hello World!"
                                                   :author "http://localhost:80/user/1"})))))
    (testing "update resource using PUT"
      (is (submap? {:status 204, :body nil}
                   (api :put "/user/1" {} (pr-str {:email "torvalds@linux.com", :name "Linus Torvalds",
                                                   :secret "i can divide by zero"}))))
      (let [response (api :get "/user/1" {} nil)]
        (is (= 200 (:status response)))
        (is (submap? {:id 1, :email "torvalds@linux.com", :name "Linus Torvalds"}
                     (:body response))))
      (is (submap? {:status 422, :body {:email 'missing-required-key :secret 'missing-required-key}}
                   (api :put "/user/1" {} (pr-str {:name "Linus"})))))

    (testing "update resource using PATCH"
      (is (submap? {:status 204, :body nil}
                   (api :patch "/user/1" {} (pr-str {:name "Linus"}))))
      (let [response (api :get "/user/1" {} nil)]
        (is (= 200 (:status response)))
        (is (submap? {:id 1, :email "torvalds@linux.com", :name "Linus"}
                     (:body response)))))

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
