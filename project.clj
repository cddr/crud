(defproject crud "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [crypto-password "0.1.3"]
                 [com.datomic/datomic-free "0.9.4899"]

                 [cddr/integrity "0.3.1-SNAPSHOT"]
                 [ring/ring-json "0.3.1"]
                 [ring/ring-defaults "0.1.2"]
                 [compojure "1.1.9"]

                 [prismatic/schema "0.2.6"]
                 [clj-time "0.8.0"]
                 [cheshire "5.3.1"]]
  :plugins [[lein-ring "0.8.12"]]
  :ring {:handler crud.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [ring-mock "0.1.5"]]}})
