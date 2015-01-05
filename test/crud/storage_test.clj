(ns crud.storage-test
  (:require [clojure.test :refer :all]
            [crud.storage :refer :all]))

(defn mock-entity [& storables]
  {:storable storables})

(deftest test-storable
  (testing "storage-name"
    (is (= :foo (storage-name :foo)))
    (is (= :bar (storage-name {:name :bar})))
    (is (nil? (storage-name "foo"))))

  (testing "storable?"
    (let [foo (mock-entity :foo)]
      (is (storable? foo :foo))
      (is (not (storable? foo :bar))))))

    
