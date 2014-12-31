(defproject crud "0.1.0-SNAPSHOT"
  :description "Create, Read, Update, Delete. Done!"
  :url "http://github.com/cddr/crud"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :scm {:url "git@github.com:cddr/crud.git"}
  :min-lein-version "2.0.0"

  :dependencies [[org.clojure/clojure "1.6.0"]
                 [com.datomic/datomic-free "0.9.4899"]

                 ;; http api
                 [compojure "1.1.9"]
                 [liberator "0.12.2"]
                 [ring/ring-defaults "0.1.2"]

                 ;; data modelling
                 [cddr/integrity "0.3.1-20141004.001913-1" :exclusions [org.clojure/clojure]]
                 [clj-time "0.8.0"]
                 [prismatic/schema "0.2.6"]]
  :plugins [[lein-ring "0.8.12"]]
  :ring {:handler crud.handler/app}
  :profiles
  {:dev {:dependencies [[javax.servlet/servlet-api "2.5"]
                        [crypto-password "0.1.3"]
                        [ring-mock "0.1.5"]]}})
