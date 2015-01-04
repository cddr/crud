(ns crud.schema-test
  (:require [clojure.test :refer :all]
            [schema.core :as s :refer [Int Str]]
            [crud.schema :refer :all]))

(deftest test-schema
  (testing "parsed-value"
    (let [user {:id Int
                :name Str}]
      (is (= {:id 42, :name "Andy"}
             (parsed-value user {:id "42", :name "Andy"})))))

  (testing "optionalize"
    (let [user {:id Int
                :name Str}]
      (is (= {(s/optional-key :id) Int
              (s/optional-key :name) Str}
             (optionalize user))))))
