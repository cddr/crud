(in-ns 'crud.resource)

;;## CRUD Protocol
;;
;; This file defines the basic protocol for handling CRUD HTTP requests in a generic way. The "!" at the end of a
;; method name below indicates that it is likely to add new facts (or replace existing ones). Each of the functions
;; here return a function designed to be used in the liberator web-machine. That is, they accept a context map
;; containing the the HTTP request and response along with any other keys added by prior steps in the machine

(defn creator! [c data-path]
  (fn [ctx]
    (apply-tx c (d/tempid :db.part/user) (get-in ctx data-path))))

(defn validator [schema parsed-input-path valid-input-path error-input-path]
  (fn [ctx]
    (let [validate-with (fn [s]
                          (let [validator (coercer s string-coercion-matcher)
                                validated (validator (get-in ctx parsed-input-path))]
                            (if (schema.utils/error? validated)
                              [false (assoc-in {} error-input-path validated)]
                              [true (assoc-in {} valid-input-path validated)])))]
      (if (get? ctx)
        (validate-with (optionalize schema))
        (validate-with schema)))))

(defn malformed? [input-path output-path]
  (fn [ctx]
    (try
      [false (assoc-in {} output-path (clojure.edn/read-string (get-in ctx input-path)))]
      (catch RuntimeException e
        [true {:parser-error (.getLocalizedMessage e)}]))))

(defn redirector [id-path]
  (fn [ctx]
    (let [request (get-in ctx [:request])]
      (URL. (format "%s://%s:%s%s/%s"
                    (name (:scheme request))
                    (:server-name request)
                    (:server-port request)
                    (:uri request)
                    (get-in ctx id-path))))))

(defn handler [db cardinality resource input-path]
  (let [{:keys [schema refs]} resource
        handle (fn [e] (as-response e schema refs))]
    (fn [ctx]
      (let [entities (find-entities db (get-in ctx input-path))]
        (case cardinality
          :collection (into [] (map handle entities))
          :single (handle (first entities)))))))
