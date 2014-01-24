(defproject digitalocean "0.1.0-SNAPSHOT"
  :description "Clojure Digital Ocean Client"
  :url "http://owainlewis.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-midje "3.1.1"]]
  :dependencies [[org.clojure/clojure "1.4.0"]
                 [cheshire "5.3.0"]
                 [midje "1.5.1"]
                 [http-kit "2.1.16"]])
