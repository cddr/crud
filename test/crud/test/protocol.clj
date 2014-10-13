(in-ns 'crud.test.resource)

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
