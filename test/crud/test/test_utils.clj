(in-ns 'crud.test.resource)

(defn path-prefix [resource] (str "/" (-> resource :name clojure.string/lower-case)))
(defn submap?     [a b]      (clojure.set/subset? (set a) (set b)))
(defn test-ids    [n]        (repeatedly n (partial d/tempid :db.part/user)))

(defn keep-one-ident [acc next]
  (if (some #(= (:db/ident %)
                (:db/ident next)) acc)
    acc
    (conj acc next)))

(defn dbg-handler [handler msg]
  (fn [req]
    (let [resp (handler req)]
      resp)))

(def test-uri "datomic:mem://test-db")

(defn test-setup []
  (d/delete-database test-uri)
  (d/create-database test-uri)
  (d/connect test-uri))

(defn test-teardown []
  (d/delete-database test-uri))

(defn test-db [test-env]
  (let [{:keys [resources test-data]} test-env
        tx (fn [cx tx-data] @(d/transact cx tx-data) cx)
        meta (reduce keep-one-ident [] (vec (mapcat r/datomic-schema
                                                    (map :schema resources)
                                                    (map :uniqueness resources)
                                                    (map :refs resources))))]
    (let [c (test-setup)]
      (tx c meta)
      (tx c test-data)
      {:cnx c
       :db (fn []
             (d/db c))})))

(defn test-app [resources test-data]
  (let [{:keys [cnx]} (test-db {:resources resources
                                :test-data (vec (reduce concat [] test-data))})]
    (apply c/routes
           (map (fn [r]
                  (c/context (path-prefix r) []
                    (r/api-routes cnx r)))
                resources))))

(defn make-client [app content-type]
  (let [parse-response (fn [response]
                         (if (:body response)
                           (assoc response :body (clojure.edn/read-string (slurp (:body response))))
                           :no-response))]
    (fn client
      ([method path]
         (client method path {} {}))
      ([method path params]
         (client method path params {}))
      ([method path params body]
         (-> (ring.mock.request/request method path params)
             (ring.mock.request/content-type "application/edn")
             (ring.mock.request/body (pr-str (or body {})))
             app
             parse-response)))))

(defn mock-api [cx resources & request-args]
  (let [app (apply c/routes (for [r resources]
                              (c/context (path-prefix r) []
                                (r/api-routes cx r))))
        parse-response (fn [response]
                         (assoc response :body (clojure.edn/read-string (slurp (:body response)))))]
    (let [[method path params body] request-args]
      (-> (ring.mock.request/request method path params)
          (ring.mock.request/content-type "application/edn")
          (ring.mock.request/body (str (or body {})))
          app
          parse-response))))

;; (defn mock-api-for [test-env]
;;   (let [{:keys [resources test-data]} test-env
;;         keep-one-ident (fn [acc next]
;;                          (if (some #(= (:db/ident %)
;;                                        (:db/ident next)) acc)
;;                            acc
;;                            (conj acc next)))
;;         tx (fn [cx tx-data]
;;              @(d/transact cx tx-data)
;;              cx)
;;         meta (reduce keep-one-ident [] (into [] (mapcat r/datomic-schema resources)))
;;         data (r/datomic-facts test-data)]
;;     (partial mock-api (-> (test-setup) (tx meta) (tx data)) resources)))

(defn test-connection [test-env]
  (let [{:keys [resources test-data]} test-env
        tx (fn [cx tx-data]
             @(d/transact cx tx-data)
             cx)
        meta (reduce keep-one-ident [] (into [] (mapcat r/datomic-schema
                                                        (map :schema resources)
                                                        (map :uniquness resources)
                                                        (map :refs resources))))
        data (r/as-facts test-data)]
    (-> (test-setup) (tx meta) (tx data))))



