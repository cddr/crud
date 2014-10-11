(ns crud.resource
  "Maps datomic attribute definitions to prismatic schemata
and vice versa"
  (:require [datomic.api :as d]
            [schema.core :as s :refer [Str Num Inst Int Bool Keyword]]
            [schema.coerce :refer [coercer string-coercion-matcher]]
            [integrity.datomic :as dat]
            [compojure.core :as http]
            [compojure.route :as route]
            [liberator.core :as rest]
            [ring.util.response :as resp])
  (:import [java.net URL URI]))

(load "datomic")
(load "query")
(load "helpers")
(load "protocol")

;; (defn api-routes [cnx & definition]
;;   (let [{:keys [name schema uniqueness refs]} definition
;;         with-overrides (fn [& b] (merge (apply hash-map b) definition))]
;;     (route/routes
;;      ;; a collection of resources can be queried, or appended to
;;      (route/ANY "/" []
;;        (rest/resource
;;         (with-overrides
;;           :available-media-types ["application/edn"]
;;           :allowed-methods       [:get :post]
;;           :known-content-type?   known-content-type?
;;           :malformed?            (parser [::parsed-input])
;;           :processable?          (validator schema [::parsed-input] [::valid-parsed-input])
;;           :post!                 (creator! cnx [::valid-parsed-input])
;;           :post-redirect         true
;;           :location              (redirector name [::valid-parsed-input :id])
;;           :handle-ok             (handler (d/db cnx) :collection definition [::valid-parsed-input])))))))


;;      ;; a single resource can be returned, patched, put, or deleted
;;      (route/ANY "/:id" [id] (rest/resource
;;                              (with-overrides
;;                                :allowed-methods     [:get :patch :put :delete]
;;                                :known-content-type? known-content-type?
;;                                :exists?             (finder (d/db cnx) id [::entity])
;;                                :malformed?          (parser [::parsed-input])
;;                                :can-put-to-missing? true
;;                                :new?                (has-path? [::entity])
;;                                :put!                (replacer! cnx [::entity] [::valid-parsed-input])
;;                                :delete!             (deleter! cnx [::entity])
                               


                               
                       

;; (defrecord Resource [name schema uniqueness])

;; (defmacro defresource [name body]
;;   `(def ~name
;;      (map->Resource (merge {:name (name '~name)}
;;                            ~body))))

;; (defn parse-with [params schema]
;;   (let [parser (coercer schema string-coercion-matcher)]
;;     (parser params)))

;; (defn datomic-facts
;;   ([tmp-id object]
;;      "Generates datomic facts by recursively walking the specified map converting
;; (key val) -> [:db/add tmp-id attr val]

;; For any values that are themselves maps, we recur on the map and add a ref for
;; the current key"
;;      (reduce (fn [acc [k v]]
;;                (if (map? v)
;;                  (into acc (let [ref-id (d/tempid :db.part/user)
;;                                  ret (concat [[:db/add tmp-id k ref-id]]
;;                                              (datomic-facts ref-id (into {} v)))]
;;                              ret))
;;                  (do
;;                    (into acc [[:db/add tmp-id k v]]))))
;;              []
;;              (seq object)))
;;   ([objects]
;;      (mapcat (fn [obj]
;;                (datomic-facts (d/tempid :db.part/user) obj))
;;              objects)))

;; (defn render-error [params resource]
;;   (str "Failed to find " (:name resource) " with query: " params))

;; (defn render-entity [e params resource]
;;   (if e
;;     (resp/response (as-tree e resource))
;;     (resp/not-found {:error (render-error params)})))

;; (defn post-tx [e params]
;;   ;; TODO: ignoring resource for now but it might be useful to allow
;;   ;;       the resource to set the tempid partition that gets used here
;;   (datomic-facts e params))

;; (defn patch-tx [e req]
;;   [e (datomic-facts e (:params req))])

;; (defn delete-tx [e]
;;   [e [[:db.fn/retractEntity (:id e)]]])

;; (defn put-tx [e req]
;;   (into [e]
;;         (concat (delete-tx e req)
;;                 (post-tx (d/tempid :db.part/user) req))))

;; (defn keywordize
;;   "Recursively convert maps in m (including itself)
;;    to have keyword keys instead of string"
;;   [x]
;;   (cond
;;    (map? x) (map (fn [[k v]]
;;                    [(if (string? k) (keyword k) k) (keywordize v)])
;;                  (seq x))
;;    (seq? x) (map keywordize x)
;;    (vector? x) (mapv keywordize x)
;;    :else x))

;; (def next-id (let [i (atom 0)]
;;                (fn []
;;                  (swap! i inc)
;;                  @i)))

;; (defn wrap-with-id [params]
;;   (if-not (:id params)
;;     (assoc params
;;       :id (next-id))
;;     params))

;; (defn lookup [attr] (fn [x]
;;                       (if (instance? datomic.Entity x)
;;                         (attr x)
;;                         [attr x])))

;; (defn wrap-with-lookup-refs [params refs]
;;   (let [reducer (fn [m [k v]]
;;                   (if-let [internalize (get refs k)]
;;                     (assoc m k (internalize v))
;;                     (assoc m k v)))]
;;     (reduce reducer {} params)))

;; (defn uri-for [name k]
;;   (fn [ctx]
;;     (format "/%s/%s" (clojure.string/lower-case name) (get-in ctx k))))

;; (defn api-routes [c resource]
;;   (let [schema (:schema resource)]
;;     (http/routes
;;      (http/POST  "/" request (let [params (-> (:body-params request)
;;                                               wrap-with-id
;;                                               (parse-with schema)
;;                                               (wrap-with-lookup-refs resource))]
;;                                (apply-tx c (d/tempid :db.part/user) params)
;;                                {:status 202
;;                                 :headers {"Location" (uri-for resource (:id params))}
;;                                 :body {}}))

;;      (http/GET   "/:id" [id]
;;        (if-let [e (find-by (d/db c) :id (clojure.edn/read-string id))]
;;          (resp/response (as-tree e resource))
;;          (resp/not-found {:error (str "Failed to find " (:name resource) " with id: " id)})))

;;      (http/PATCH "/:id" [id :as request]
;;                  (if-let [e (find-by (d/db c) :id (clojure.edn/read-string id))]
;;                    (let [params (parse-with (:body-params request) (optionalize schema))]
;;                      (apply-tx c (:db/id e) params)
;;                      {:status 202, :body {}})))
                               
;;      (http/GET   "/" request (let [params (-> (:params request)
;;                                               clojure.walk/keywordize-keys
;;                                               (parse-with (optionalize schema)))]
;;                                (if (schema.utils/error? params)
;;                                  {:status 422
;;                                   :body {:error (schema.utils/error-val params)}}
;;                                  (if-let [e (find-entity (wrap-with-lookup-refs params resource) (d/db c))]
;;                                    (cond
;;                                     (< 1 (count e)) (resp/response (into [] (map #(as-tree % resource) e)))
;;                                     (= 1 (count e)) (resp/response (as-tree (first e) resource))
;;                                     :else (resp/not-found {:error (str "Failed to find " (:name resource)
;;                                                                        " with query: " (:params request))}))))))


;;      (route/not-found {:body {:error "Endpoint not found"}}))))
     

;;      ;; (http/PATCH "/" request (-> request
;;      ;;                             (parse-with schema)
;;      ;;                             (apply-tx c (partial patch-tx (:id (find-entity request))))))

;;      ;; (http/PUT   "/" request (-> request
;;      ;;                             (parse-with schema)
;;      ;;                             (apply-tx c (partial put-tx (:id (find-entity request))))))

;;      ;; (http/DELETE "/" request (-> request
;;      ;;                              (parse-with schema)
;;      ;;                              (apply-tx c (partial delete-tx (:id (find-entity request)))))))))


;; ;; (def Recipient
;; ;;   {:schema {:first-name s/Str
;; ;;             :last-name s/Str}
;; ;;    :uniqueness {:id :db.unique/entity}})

;; ;; (defn error-response [errors]
;; ;;   {:status 422
;; ;;    :body errors})

;; ;; (defn finder [resource]
;; ;;   (fn [db params]
;; ;;     (d/entity db params)))

;; ;; (defn transactor [resource]
;; ;;   (fn [cnx params]
;; ;;     (let [errors (s/check resource params)]
;; ;;       (if errors
;; ;;         (error-response errors)
;; ;;         (let [tempid #db/id[:db.part/user]
;; ;;               tx-data (dat/datomic-facts map tempid)]
;; ;;           (let [tx @(d/transact cnx [tx-data])]
;; ;;             (d/resolve-tempid (:db-after tx)
;; ;;                               (:tempids tx)
;; ;;                               tempid)))))))


      

;; ;; (defprotocol HttpResource
;; ;;   (id-lookup [resource id] "")
;; ;;   (create [resource params] 

    

;; ;; (defmacro defentity [name & body]
;; ;;   `(do
;; ;;      (def ~name
;; ;;        {:schema ~@body
;; ;;         :get (fn [id]
;; ;;                (d/entity id))
;; ;;         :post (fn [params]
;; ;;                 (if (s/check ~name params)
                  

;; ;; (defentity foo {:foo s/Str})


;; ;; ;; (def Ref {:schema (s/either Int Keyword)
;; ;; ;;           :attr-factory (fn [ident]
;; ;; ;;                           {:db/id (d/tempid :db.part/db)
;; ;; ;;                            :db/ident ident
;; ;; ;;                            :db/valueType :db.type/ref
;; ;; ;;                            :db/cardinality :db.cardinality/one
;; ;; ;;                            :db.install/_attribute :db.part/db})})

;; ;; ;; (def ^{:private true}
;; ;; ;;   schema->datomic
;; ;; ;;   {Str                      :db.type/string
;; ;; ;;    Bool                     :db.type/boolean
;; ;; ;;    Long                     :db.type/long
;; ;; ;;    ;java.Math.BigInteger     :db.type/bigint
;; ;; ;;    Num                      :db.type/double
;; ;; ;;    Int                      :db.type/integer
;; ;; ;;    Float                    :db.type/float
;; ;; ;;    Inst                     :db.type/instant
;; ;; ;;    ;java.Math.BigDecimal     :db.type/bigdec
;; ;; ;;    })

;; ;; ;; (defmulti attribute
;; ;; ;;   "Implementing methods should return a function that will generate a
;; ;; ;; datomic attribute when given it's id as the one and only argument"
;; ;; ;;   (fn [ident schema]
;; ;; ;;     (class schema)))

;; ;; ;; (defmethod attribute ::leaf [ident schema]
;; ;; ;;   {:db/id (d/tempid :db.part/db)
;; ;; ;;    :db/ident ident
;; ;; ;;    :db/valueType ((comp val find) schema->datomic schema)
;; ;; ;;    :db/cardinality :db.cardinality/one
;; ;; ;;    :db.install/_attribute :db.part/db})

;; ;; ;; (defmethod attribute ::vector [ident schema]
;; ;; ;;   {:db/id (d/tempid :db.part/db)
;; ;; ;;    :db/ident ident
;; ;; ;;    :db/valueType ((comp val find) schema->datomic (first schema))
;; ;; ;;    :db/cardinality :db.cardinality/many
;; ;; ;;    :db.install/_attribute :db.part/db})

;; ;; ;; (defn attributes [schema]
;; ;; ;;   "Given a prismatic schema, returns a list of datomic attributes"
;; ;; ;;   (let [mk-attr (fn [k v]
;; ;; ;;                   (attribute k v))]
;; ;; ;;     (reduce (fn [acc [k v]]
;; ;; ;;               (if (map? v)
;; ;; ;;                 (into acc (conj (attributes (into {} v))
;; ;; ;;                                 ((:attr-factory Ref) k)))
;; ;; ;;                 (into acc [(mk-attr k v)])))
;; ;; ;;             []
;; ;; ;;             (seq schema))))

;; ;; ;; (derive java.lang.Class                ::leaf)

;; ;; ;; ;; This is only required because some single valued Schema types (e.g. Int)
;; ;; ;; ;; are implemented as predicates. We're not trying to generate datomic attributes
;; ;; ;; ;; for arbitrary predicates
;; ;; ;; (derive schema.core.Predicate          ::leaf)

;; ;; ;; (derive clojure.lang.IPersistentVector ::vector)

