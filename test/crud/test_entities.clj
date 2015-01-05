(ns crud.test-entities
  (:require [crud.core :as r]
            [crypto.password.bcrypt :as password]
            [schema.core :as s :refer [Str Bool Num Int Inst Keyword]]))

(defn encrypt [attr]
  ;; The 4 here is so we're not slowing our tests down. IRL you should use at least 10
  {:name attr, :callable (fn [val] (password/encrypt val 4))})

(r/defentity User
  :schema {:id Int
           :email Str
           :name Str
           :secret Str}
  :storable [:id :email :name (encrypt :secret)]
  :uniqueness {:id :db.unique/identity})

(r/defentity Tweet
  :schema {:id Int
           :body Str
           :author Str}
  :uniqueness {:id :db.unique/identity}
  :refs [(r/build-ref User :author :id)])

(r/defentity StorageTest
  :schema {:id Int
           :attr Int
           :ignored Int}
  :storable [:id :attr])

