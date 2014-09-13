(ns digitalocean.v2.core
  (:require [cheshire.core :as json]
	    [schema.core :as scm]
            [clojure.java.io :as io]
	    [org.httpkit.client :as http]))

(def endpoint "https://api.digitalocean.com/v2/")

(defn load-dev-token
  "Loads temporary token for development
   can be removed at some point"
  []
  (let [path "/Users/owainlewis/.auth/DIGITALOCEAN.txt"]
    (with-open [rdr (io/reader path)]
      (first (take 1 (line-seq rdr))))))

(defn run-request
  "Utility method for making HTTP requests
   to the Digital Ocean API"
  [method url token & params]
  (let [all-params (into {} params)
        {:keys [status headers body error] :as resp}
          @(http/request
            {:method method
             :url url
             :form-params all-params
             :headers {"Authorization" (str "Bearer " token)}})]
  (if (nil? error)
    (json/parse-string body true)
    {:error error})))

(defn resource-url
  "Helper function that builds url endpoints
   (resource-url :domains 1 2 3) =>
     https://api.digitalocean.com/v2/domains/1/2/3
  "
  [resource & parts]
  (let [nested-url-parts (apply str (interpose "/" (into [] parts)))
        qualified-resource (name resource)]
    (str endpoint qualified-resource "/" nested-url-parts)))

;; Domains

(defn domains
  "Returns all domains for a digital ocean account"
  [token]
  (run-request :get (resource-url :domains) token))

(defn domain
  "Return a single domain by name i.e (domain token \"mysite.com\")"
  [token name]
  (run-request :get (resource-url :domains name) token))

;; Droplets

(defn droplets
  [token]
  (run-request :get (resource-url :droplets) token))

(defn droplet
  "Find a droplet by id i.e
   (droplet token 2605916) =>
     {:droplet {:status \"active\", :vcpus 1, :name \"coreos\", :locked false}}..."
  [token droplet-id]
  (run-request :get (resource-url :droplets droplet-id) token))

(defn droplet-create
  "Create a new droplet
   Required params are :name :region :size :image"
  [token params]
  (run-request :post (resource-url :droplets) token params))
