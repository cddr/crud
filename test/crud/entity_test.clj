(ns crud.entity-test
  (:require [clojure.test :refer :all]
            [crud.entity :refer :all]
            [schema.core :as s :refer [Str Num Inst Int Bool Keyword optional-key]]))

(deftest test-read-id
  (let [mock-entity (fn [id-type]
                      (map->Entity {:schema {:id id-type}}))]
    (testing "read-id"
      (is (= "0XCAFEBABE" (read-id (mock-entity Str) "0XCAFEBABE")))
      (is (= 42 (read-id (mock-entity Int) "42"))))))

(deftest test-query-schema
  (let [mock-entity (fn [schema]
                      (map->Entity {:schema schema}))]
    (testing "all keys are optionalized"
      (is (= {(optional-key :a) Int
              (optional-key :b) Str}
             (query-schema (mock-entity {:a Int, :b Str})))))))

(deftest test-storage-agent
  (testing "keywords name and call themselves"
    (is (= {:name :foo, :callable :foo}
           (storage-agent :foo))))

  (testing "custom :name and :callable"
    (let [test-agent (fn [attr]
                       {:name attr, :callable attr})]
      (is (= {:name :foo, :callable :foo}
             (storage-agent (test-agent :foo)))))))

(deftest test-storage-schema
  (let [mock-entity (fn [args]
                      (map->Entity args))]
    (testing "no storable specified"
      (is (= {:a Int, :b Int}
             (storage-schema (mock-entity {:schema {:a Int, :b Int}})))))

    (testing "ignores attributes not in :storable"
      (is (= {:a Int}
             (storage-schema (mock-entity {:schema {:a Int, :b Int}
                                           :storable [:a]})))))

    (testing "includes schema for transformed values"
      (let [mock-agent (fn [attr]
                         {:name attr, :callable identity})]
        (is (= {:a Int, :b Int}
               (storage-schema (mock-entity {:schema {:a Int, :b Int}
                                             :storable [:a (mock-agent :b)]}))))))))

(deftest test-storable
  (testing "default case"
    (is (= {:a 1, :b 10}
           (storable nil {:a 1, :b 10}))))

  (testing "remove non-storable entries"
    (is (= {:a 1}
           (storable {:storable [:a]}
                     {:a 1, :b 10}))))

  (testing "transform using storage agents"
    (let [double-agent (fn [attr]
                         {:name attr, :callable (fn [val]
                                                  (clojure.tools.trace/trace val)
                                                  (* 2 (attr val)))})]
      (is (= {:a 2}
             (storable {:storable [(double-agent :a)]}
                       {:a 1}))))))

(deftest test-link
  (testing "construct link"
    (let [Author {:schema {:id Int}}]
      (is (= {:from :author
              :to Author
              :attr :id
              :cardinality :db.cardinality/one}
             (link :author [Author :id]))))))

