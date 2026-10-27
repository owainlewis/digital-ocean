(defproject digitalocean "2.0.0-SNAPSHOT"
  :description "A small Clojure client for the DigitalOcean API v2"
  :url "https://github.com/owainlewis/digital-ocean"
  :license {:name "Eclipse Public License"
            :url "https://www.eclipse.org/legal/epl-v10.html"}
  :scm {:name "git"
        :url "https://github.com/owainlewis/digital-ocean"}
  :min-lein-version "2.11.2"
  :dependencies [[org.clojure/clojure "1.11.4"]
                 [cheshire "6.2.0"]
                 [clj-http "3.13.1"]]
  :deploy-repositories
  [["clojars"
    {:url "https://repo.clojars.org"
     :username :env/clojars_username
     :password :env/clojars_password
     :sign-releases false}]]
  :profiles {:dev {:dependencies [[http-kit "2.8.0"]]
                   :plugins [[lein-cljfmt "0.9.2"]]}
             :clojure-1.12 {:dependencies [[org.clojure/clojure "1.12.3"]]}}
  :aliases {"ci" ["do" "check," "test," "cljfmt" "check"]})
