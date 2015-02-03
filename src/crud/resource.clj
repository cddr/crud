(ns crud.resource
  "A Crud `app` uses a CrudDB implementation to expose a hyper-media server against a set of entities. This means
  that relationships between entities can be explored programatically by inspecting links embedded in the server
  responses."
  (:require [clojure.string :refer [split]]
            [crud.db :refer [find-by-id find-by commit! retract! present]]
            [crud.entity :refer [read-id query-schema]]
            [schema.core :as s :refer [Str Num Inst Int Bool Keyword]]
            [schema.coerce :refer [coercer string-coercion-matcher]]
            [compojure.core :as http]
            [liberator.representation :refer [Representation]]
            [liberator.core :as rest :refer [by-method]]
            [clojure.string :refer [join]])
  (:import [java.net URL URI]
           [java.io File]))

(defn- validate-with [schema ctx]
  (let [parsed-input-path [::parsed-input]
        valid-input-path [::valid-parsed-input]
        error-input-path [::validation-error]

        optionalized (into {} (map (fn [[name type]]
                                     [(s/optional-key name) type])
                                   (seq schema)))

        validator #((coercer schema string-coercion-matcher) %)
        validated (validator (get-in ctx parsed-input-path))]
    (if (schema.utils/error? validated)
      [false (assoc-in {} error-input-path validated)]
      [true (assoc-in {} valid-input-path validated)])))

(defn find-by-id! [entity db id ctx]
  (if-let [entity (find-by-id db (read-id entity id))]
    [true (assoc-in ctx [:entity] entity)]
    [false (assoc-in ctx [::parsed-input :id] id)]))

(defn create! [entity db ctx]
  (commit! db entity (get-in ctx [::valid-parsed-input])))

(defn destroy! [entity db ctx]
  (retract! db entity (:entity ctx)))

(defn validate! [entity ctx]
  (if (= :get (get-in ctx [:request :request-method]))
    (validate-with (query-schema entity) ctx)
    (validate-with (:schema entity) ctx)))

(defn redirect! [ctx]
  "Used in POST requests to support replying with the location of a created resource"
  (let [request (get-in ctx [:request])]
    (URL. (format "%s://%s:%s%s/%s"
                  (name (:scheme request))
                  (:server-name request)
                  (:server-port request)
                  (:uri request)
                  (get-in ctx [::valid-parsed-input :id])))))

(defn known-content-type? [ctx]
  (if (= "application/edn" (get-in ctx [:request :content-type]))
    true
    [false {:error "Unsupported content type"}]))

(defn malformed?
  "Tries to parse validate the request body against `(:schema entity)

  If successful, return false and put the result in the ::parsed-input `ctx` key, otherwise return true and
  put any errors in the ::parser-errors `ctx` key."
  [entity ctx]
  (let [input-path [:request :body]
        output-path [::parsed-input]
        ;; TODO: This might be something you want to configure at the application
        ;;       level. Consider exposing via defentity
        {:keys [reader media-type]} {:reader clojure.edn/read-string
                                     :media-type "application/edn"}]
    (try
      (let [body-as-str (if-let [body (get-in ctx input-path)]
                          (condp instance? body
                            java.lang.String body
                            (slurp (clojure.java.io/reader body))))]
        [false (assoc-in {} output-path (reader body-as-str))])
      (catch RuntimeException e
        [true {:representation {:media-type media-type}
               :parser-error (.getLocalizedMessage e)}]))))

(defn handle-ok! [entity db cardinality ctx]
  (let [response (-> (find-by db (get-in ctx [::valid-parsed-input]))
                     (map (partial present entity)))]
    (case cardinality
      :collection response
      :single (first response))))

(defn handle-not-found! [name id]
  {:error (str "Could not find " name " with id: " id)})

(defn handle-malformed! [ctx]
  {:error (:parser-error ctx)})

(defn handle-created! []
  (pr-str "Created."))

(defn handle-deleted! []
  (pr-str "Deleted."))

(defn handle-unprocessable-entity! [ctx]
  (schema.utils/error-val (::validation-error ctx)))

;; ## Hyper Resources
;;
;; Crud generates a set of liberator resources for each entity. Together, the resources implement a hyper-media
;; server exposing an explorable REST API.
;; 
    
(defn crud-get-collection
  "Return a liberator state-machine for GET /collection"
  [entity db]
  {:available-media-types ["application/edn"]
   :allowed-methods       [:get :post]
   :known-content-type?   known-content-type?
   :malformed?            (partial malformed? entity)
   :processable?          (partial validate! entity)
   :post!                 (partial create! entity db)
   :post-redirect         true
   :location              redirect!
   :handle-ok             (partial handle-ok! entity db :collection)
   :handle-created        (pr-str "Created.")
   :handle-unprocessable-entity (comp schema.utils/error-val ::validation-error)})

(defn crud-get
  "Return a liberator state-machine for GET /resource/:id"
  [entity db id]
  {:allowed-methods [:get]
   :available-media-types        ["application/edn"]
   :known-content-type?          known-content-type?
   :exists?                      (find-by-id! entity db id)
   :handle-not-found             (handle-not-found! name id)
   :handle-ok                    (partial handle-ok! entity db :single)})

(defn crud-put
  "Return a liberator state-machine for PUT /resource/:id"
  [entity db id]
  {:allowed-methods       [:put]
   :available-media-types ["application/edn"]
   :known-content-type?   known-content-type?
   :malformed?            (partial malformed? entity)
   :processable?          (partial validate! entity)
   :exists?               (find-by-id! entity db id)
   :new?                  #(nil? (:entity %))
   :can-put-to-missing?   true
   :put!                  (partial create! entity)
   :handle-malformed      (partial handle-malformed!)
   :handle-created        (handle-created!)
   :handle-unprocessable-entity (partial handle-unprocessable-entity!)})

(defn crud-patch
  "Return a liberator state-machine for PATCH /resource/:id"
 [entity db id]
  {:allowed-methods       [:patch]
   :available-media-types ["application/edn"]
   :known-content-type?   known-content-type?
   :malformed?            (partial malformed? entity)
   :processable?          (partial validate! entity)
   :exists?               (find-by-id! entity db id)
   :handle-not-found      (handle-not-found! name id)
   :patch!                (partial create! entity)
   :handle-malformed      (partial handle-malformed!)
   :handle-unprocessable-entity (partial handle-unprocessable-entity!)})


(defn crud-delete
  "Return a liberator state-machine for DELETE /resource/:id"
  [entity db id]
  {:allowed-methods       [:delete]
   :available-media-types ["application/edn"]
   :known-content-type?   known-content-type?
   :exists?               (find-by-id! entity db id)
   :delete!               (partial destroy! entity)
   :handle-not-found      (handle-not-found! name id)
   :handle-no-content     (handle-deleted!)})

(defn api
  "Returns the HTTP hyper-media end-points supported by `entity`"
  [db entity]
  (http/routes
   (http/GET "/:id" [id]    (rest/resource (crud-get entity db id)))
   (http/PUT "/:id" [id]    (rest/resource (crud-put entity db id)))
   (http/PATCH "/:id" [id]  (rest/resource (crud-patch entity db id)))
   (http/DELETE "/:id" [id] (rest/resource (crud-delete entity db id)))
   (http/ANY "/" []         (rest/resource (crud-get-collection entity db)))))

(defn app-index [app-spec]
  (merge
   (select-keys app-spec [:title :description])
   {:links (concat [{:rel "self"
                     :href "/"}]
                   (map (fn [entity]
                          {:rel (:name entity)
                           :href (join "/" [nil (:name entity)])})
                        (:entities app-spec)))}))

(defn app
  "Returns an HTTP hyper-media server for `entities` using `db` as a storage service

  `entities` should be a sequence of entities defined using `defentity`

  `db` should be a CrudDB pre-loaded with all attributes required by `entities`"
 [app-spec]
 (http/routes
  (http/GET "/" (app-index app-spec))))

  ;; (let [prefix (fn [name]
  ;;                (str "/" (clojure.string/lower-case name)))]
    
    ;; (->> entities
    ;;      (map (juxt :name (partial api db)))
    ;;      (map #(http/context (prefix %1) [] %2))
    ;;      (apply http/routes))))
