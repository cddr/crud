(ns crud.resource-test
  "The aim here is to be the glue between HTTP resources and datomic persistence.
To do that, we provide a function that returns a set of handlers, one for each
HTTP method, that does corresponding thing on an underlying datomic database."
  (:require [clojure.test :refer :all]
            [crud.test-mocks :refer :all]
            [crud.resource :refer :all]
            [schema.core :refer [Any Str Bool Num Int Inst Keyword]]
            [crypto.password.bcrypt :as password])
  (:import [java.net URI]))

(deftest test-entities
  (testing "as-url"
    (let [user (mock-resource "user")]
      (is (= "/user/42" (as-url (user {:id 42}))))))

  (testing "as-response"
    (let [user (mock-resource "user" :schema {:id Int, :name "Andy"})]
      (is (= {:id 42, :name "Andy"} (as-response (user {:id 42, :name "Andy"})))))))

      
