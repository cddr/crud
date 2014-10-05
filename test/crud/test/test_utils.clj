(in-ns 'crud.test.resource)

(defn path-prefix [resource] (str "/" (clojure.string/lower-case (:name resource))))

(defn dbg-handler [handler msg]
  (fn [req]
    (prn "req: " msg req)
    (let [resp (handler req)]
      (prn "resp: " resp)
      resp)))

(def test-uri "datomic:mem://test-db")

(defn test-setup []
  (d/create-database test-uri)
  (d/connect test-uri))

(defn test-teardown []
  (d/delete-database test-uri))

(defn mock-api [cx resources & request-args]
  (let [app (-> (apply c/routes (for [r resources]
                                  (c/context (path-prefix r) []
                                    (r/api-routes cx r))))
                (wrap-restful-format :formats [:edn :json])
                (wrap-defaults api-defaults))
        parse-response (fn [response]
                         (assoc response :body (clojure.edn/read-string (slurp (:body response)))))]
    (let [[method path params body] request-args]
      (-> (ring.mock.request/request method path params)
          (ring.mock.request/content-type "application/edn")
          (ring.mock.request/body (str (or body {})))
          app
          parse-response))))

(defn mock-api-for [test-env]
  (let [{:keys [resources test-data]} test-env
        keep-one-ident (fn [acc next]
                         (if (some #(= (:db/ident %)
                                       (:db/ident next)) acc)
                           acc
                           (conj acc next)))
        tx (fn [cx tx-data]
             @(d/transact cx tx-data)
             cx)
        meta (reduce keep-one-ident [] (into [] (mapcat db/attributes (map :schema resources)
                                                     (map :uniqueness resources))))
        data (r/datomic-facts test-data)]
    (partial mock-api (-> (test-setup) (tx meta) (tx data)) resources)))

