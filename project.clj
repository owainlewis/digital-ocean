(defproject digitalocean "1.2"
  :description "Clojure Digital Ocean Client"
  :url "http://owainlewis.com"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :plugins [[lein-midje "3.1.3"]]
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [cheshire "5.3.1"]
                 [prismatic/schema "0.2.0"]
                 [midje "1.6.0"]
                 [http-kit "2.1.18"]])
