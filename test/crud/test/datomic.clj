(in-ns 'crud.test.resource)

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

    
