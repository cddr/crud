(in-ns 'crud.core-test)

;; HTTP Response Helpers
(defn- status= [status] (fn [response]
                          (= status (:status response))))

(def ok?          (status= 200))
(def created?     (status= 201))
(def bad-request? (status= 400))
(def not-found?   (status= 404))
(def invalid?     (status= 422))

(def body?        (fn [response body]
                    (= body (get-in response [:body]))))
(def error?       (fn [response error]
                    (= error (get-in response [:body :error]))))

(defn submap?     [a b]      (clojure.set/subset? (set a) (set b)))
(defn test-ids    [n]        (repeatedly n (partial d/tempid :db.part/user)))

(defn dbg-handler [handler msg]
  (fn [req]
    (let [resp (handler req)]
      resp)))

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

