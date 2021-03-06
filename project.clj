(defproject protean "0.13.0"
  :description "Take control of your RESTful API's, simulate, doc, test easily."
  :url "http://github.com/passivsystems/protean"
  :license {:name "Apache License v2.0"
            :url "http://www.apache.org/licenses/LICENSE-2.0"}
  :min-lein-version "2.0.0"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [org.clojure/data.xml "0.0.8"]
                 [org.clojure/core.incubator "0.1.4"]
                 [org.clojure/tools.cli "0.4.1"]
                 [org.clojure/tools.namespace "0.2.11"]
                 [ring/ring-jetty-adapter "1.7.1"]
                 [ring/ring-core "1.7.1"]
                 [ring/ring-codec "1.1.1"]
                 [compojure "1.6.1"]
                 [cheshire "5.8.1"]
                 [com.taoensso/timbre "4.10.0"]
                 [clj-http "3.9.1"]
                 [io.aviso/pretty "0.1.37"]
                 [expectations "2.1.10"]
                 [me.rossputin/diskops "0.8.0"]
                 [org.silkyweb/silk "0.14.0"]
                 [alandipert/enduro "1.2.0"]
                 [org.slf4j/slf4j-simple "1.7.25"]
                 [aysylu/loom "1.0.2"]
                 [joda-time/joda-time "2.10.1"]
                 [clj-time "0.15.0"]
                 [org.tcrawley/dynapath "0.2.5"]
                 [com.cemerick/pomegranate "1.1.0"]
                 [json-path "1.0.1"]
                 [protean-api "0.13.0"]
                 [hawk "0.2.11"]]
  :plugins [[lein-ring "0.12.4"]
            [lein-expectations "0.0.8"]]
  :aot :all
  :uberjar-name ~(str (-> "project.clj" slurp read-string (nth 1)) "-" (-> "project.clj" slurp read-string (nth 2)) "-standalone.jar")
  :main protean.cli.main)
