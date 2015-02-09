(ns crud.hyperclient
  (:require [ring.mock.request :as mock])
  (:import [java.net URL URI]))

(defprotocol HyperClient
  (current [this])
  (back [this])
  (history [this])
  (invoke
    [this method uri body]))

(defn location [client]
  (get-in (current client) [:headers "Location"]))

(defn body [client]
  (:body (current client)))

(defn status [client]
  (:status (current client)))

(defn links [client]
  (:_links (body client)))

(defn follow [client link {:keys [method body]
                           :or {method :get
                                body nil}}]
  (invoke client method (:href link) body))

(defn rel= [name]
  (fn [link]
    (= name (:rel link))))

(defn name= [name]
  (fn [link]
    (= name (:name link))))

(defn follow-redirect [client]
  (invoke client :get (location client) nil))

(defn follow-collection [client]
  (let [coll (->> (links client)
                  (filter (rel= "collection"))
                  first)]
    (invoke client :get (:href coll) nil)))

(defn- wrap-request [params]
  (fn [request]
    (-> request
        (mock/content-type "application/edn")
        (mock/header "Accept" "application/edn")
        (mock/body (pr-str params)))))

(defn- wrap-response []
  (fn [response]
    (assoc response
           :body (clojure.edn/read-string (:body response)))))

(defn hyperclient [start-point hyperserver]
  (let [history (atom ())
        request (fn [method uri params]
                  (let [app (comp (wrap-response)
                                  hyperserver
                                  (wrap-request params))]
                    (app (mock/request method uri))))]

    ;; navigate to start-point
    (swap! history conj (request :get start-point nil))

    (reify HyperClient
      (current [this]
        (first @history))
      (back [this]
        (swap! history pop))
      (history [this]
        @history)
      (invoke [this method uri params]
        (swap! history conj (request method uri params))))))
