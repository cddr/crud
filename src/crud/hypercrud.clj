(ns crud.hypercrud
  "A Crud `app` uses a CrudDB implementation to expose a hyper-media server against a set of entities. This means
  that relationships between entities can be explored programatically by inspecting links embedded in the server
  responses."
  (:require [clojure.string :refer [split join]]
            [bidi.bidi :refer [match-route path-for]]
            [bidi.ring :refer [make-handler]]
            [crud.machine :refer :all]
            [crud.entity :refer [routes publish-link]]
            [liberator.core :refer [resource by-method]]))
            
(defn find-entity [app-spec req]
  (let [found? (fn [entity]
                 (= (-> req :route-params :entity)
                    (:name entity)))]
    (->> (:entities app-spec)
         (filter found?)
         first)))

(defn index-handler [app-spec]
  (fn [req]
    (let [links {:item (for [entity (:entities app-spec)]
                         (publish-link :collection
                                       {:name (:name entity)
                                        :entity (:name entity)}))}

          handler (resource {:allowed-methods [:get]
                             :available-media-types ["application/edn"]
                             :handle-ok {:_links links}})]
      (handler req))))

(defn collection-handler [app-spec]
  (fn [req]
    (if-let [entity (find-entity app-spec req)]
      (do
        (let [handler (resource (crud-collection entity (:db app-spec)))]
          (handler req))))))

(defn resource-handler [app-spec]
  (fn [req]
    (if-let [entity (find-entity app-spec req)]
      (let [id (-> req :route-params :id)
            handler (resource (case (:request-method req)
                                :get (crud-get entity (:db app-spec) id)
                                :delete (crud-delete entity (:db app-spec) id)))]
        (handler req)))))
      
(defn hypercrud [app-spec]
  (let [handlers {:collection (collection-handler app-spec)
                  :resource (resource-handler app-spec)
                  :index (index-handler app-spec)}
        find-handler (fn [match]
                       (match handlers))]
    (make-handler routes find-handler)))
