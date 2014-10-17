(defproject crud "0.1.0-SNAPSHOT"
  :description "Create, Read, Update, Delete. Done!"
  :url "http://github.com/cddr/crud"
  :min-lein-version "2.0.0"
  :dependencies [[cddr/integrity "0.3.1-SNAPSHOT"]
                 [cheshire "5.3.1"]
                 [clj-time "0.8.0"]
                 [com.datomic/datomic-free "0.9.4899"]
                 [compojure "1.1.9"]
                 [liberator "0.12.2"]
                 [org.clojure/clojure "1.6.0"]
                 [org.clojure/test.check "0.5.9"]
                 [prismatic/schema "0.2.6"]
                 [ring-middleware-format "0.4.0"]
                 [ring/ring-defaults "0.1.2"]]
  :plugins [[lein-ring "0.8.12"]]
  :ring {:handler crud.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [crypto-password "0.1.3"]
                        [ring-mock "0.1.5"]]}})
