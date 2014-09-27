(ns digitalocean.v2.core
  (:import [java.net URLEncoder])
  (:refer-clojure :exclude [keys])
  (:require [cheshire.core :as json]
	    [schema.core :as scm]
            [clojure.java.io :as io]
	    [org.httpkit.client :as http]))

;; _____ _____ _____ _____ _______       _         ____   _____ ______          _   _
;; |  __ \_   _/ ____|_   _|__   __|/\   | |       / __ \ / ____|  ____|   /\   | \ | |
;; | |  | || || |  __  | |    | |  /  \  | |      | |  | | |    | |__     /  \  |  \| |
;; | |  | || || | |_ | | |    | | / /\ \ | |      | |  | | |    |  __|   / /\ \ | . ` |
;; | |__| || || |__| |_| |_   | |/ ____ \| |____  | |__| | |____| |____ / ____ \| |\  |
;; |_____/_____\_____|_____|  |_/_/    \_\______|  \____/ \_____|______/_/    \_\_| \_|


(def endpoint "https://api.digitalocean.com/v2/")

(defn load-dev-token
  "Loads temporary token for development
   can be removed at some point"
  []
  (let [path "/Users/owainlewis/.auth/DIGITALOCEAN.txt"]
    (with-open [rdr (io/reader path)]
      (first (take 1 (line-seq rdr))))))

;; **************************************************************

(defn run-request
  "Utility method for making HTTP requests
   to the Digital Ocean API"
  [method url token & params]
  (let [all-params (into {} params)
        headers {"Content-Type" "application/json"
                 "Authorization" (str "Bearer " token)}
        mergeable (if (empty? all-params)
                    (hash-map)
                    {:body (json/encode all-params)})
        {:keys [status headers body error] :as resp}
          @(http/request
            (merge
              {:method method
               :url url
               :headers headers} mergeable))]
  (if (nil? error)
    (json/parse-string body true)
    {:error error})))

(defn normalize-url [url]
  (if (string? url)
    (URLEncoder/encode
      (clojure.string/lower-case url))
    url))

(defn resource-url
  "Helper function that builds url endpoints
   (resource-url :domains 1 2 3) =>
     https://api.digitalocean.com/v2/domains/1/2/3
  "
  [resource & parts]
  (let [nested-url-parts 
         (apply str 
           (interpose "/" 
             (map normalize-url (into [] parts))))
        qualified-resource (name resource)]
    (str endpoint qualified-resource "/" nested-url-parts)))

;; Generics
;; **************************************************************

(defn generic
  "The function does the bulk of the work in abstracting away repetitive
   REST like requests.
   i.e (generic :get :domains) => (fn [token] ;; domain fetching logic)"
  [method resource]
  (let [request-builder (fn [token url-identifiers & params]
                          (let [resource-endpoint
                            (-> (partial resource-url (name resource))
                                (apply url-identifiers))]
    (run-request method 
                 resource-endpoint 
                 token 
                 (into {} params))))]
  (fn
    ([token]
      (request-builder token [] {}))
    ([token resource-identifier & params]
      (request-builder token [resource-identifier] (into {} params))))))

;; Domains
;; **************************************************************

(def domains
  "Fetch all domains"
  (generic :get :domains))

(def get-domain
  "Get a single domain by name"
  domains)

;; Records
;; **************************************************************

(defn records
  "Return all records for a domain"
  [token domain]
  (run-request :get
    (resource-url (str "domains/" domain "/records"))
      token))

;; Droplets
;; **************************************************************

(def droplets
  "Get all droplets"
  (generic :get :droplets))

(def get-droplet
  "Get a single droplet by ID"
  droplets)

(def create-droplet
  "Create a new droplet"
  (generic :post :droplets))

;; Images
;; **************************************************************

(def images "Return all images"
  (generic :get :images))

(def get-image images)

;; Keys
;; **************************************************************

(def ssh-keys "Get all account SSH keys"
  (generic :get "account/keys"))

(def get-key ssh-keys)

(def create-key
  "Create a new SSH key"
  (generic :post "account/keys"))

;; Regions
;; **************************************************************

(def regions
  "Returns all Digital Ocean regions"
  (generic :get :regions))

;; Sizes
;; **************************************************************

(def sizes
  "Returns droplet sizes for Digital Ocean images"
  (generic :get :sizes))
