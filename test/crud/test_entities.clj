(ns crud.test-entities
  (:require [crud.entity :refer :all]
            [crypto.password.bcrypt :as password]
            [schema.core :as s :refer [Str Bool Num Int Inst Keyword]]))

(defn encrypt [attr]
  ;; The 4 here is so we're not slowing our tests down. IRL you should use at least 10
  {:name attr, :callable (fn [val] (password/encrypt val 4))})

(defentity User
  :schema {:id Int
           :email Str
           :name Str
           :secret Str}
  :storable [:id :email :name (encrypt :secret)]
  :uniqueness {:id :db.unique/identity})

(defentity Tweet
  :schema {:id Int
           :body Str
           :author Str}
  :uniqueness {:id :db.unique/identity}
  :links [(link :author [User :id])])

(defentity StorageTest
  :schema {:id Int
           :attr Int
           :ignored Int}
  :storable [:id :attr])

