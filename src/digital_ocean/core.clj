(ns digital-ocean.core
  ^{:doc "A Clojure wrapper for the Digital Ocean API"
    :author "Owain Lewis"}
  (:require [cheshire.core :as json]
            [org.httpkit.client :as http]))

(def do "https://api.digitalocean.com")

(defn env 
  "Fetch an env var"
  [k] (System/getenv k))

(defn creds [] 
  { :client (env "DO_CLIENT") 
    :key    (env "DO_KEY") })

;; Helper methods
;; **************************************************************

(defn url-encode-params 
  ""
  [params-map]
  (into {}
    (for [[k v] params-map]
      [k (java.net.URLEncoder/encode v)])))

(defn make-query-params 
  "Build query params"
  [client-id api-key & params]
  (let [base-params  (format "?client_id=%s&api_key=%s" client-id api-key)
        extra-params (apply str
                       (for [[k v] (url-encode-params (into {} params))]
                         (str "&" (if (keyword? k) 
                                    (name k) k) "=" v)))]
    (if (clojure.string/blank? extra-params)
      base-params
      (format "%s%s" base-params extra-params))))

(defn url-with-params 
  [endpoint client-id api-key & params]
  (let [query-params (make-query-params client-id api-key (into {} params))
        url (format "%s/%s%s" do endpoint query-params)]
    url))

;; HTTP request
;; **************************************************************
  
(defn request 
  "Make a simple request. We are only dealing with GET requests
   for this particular API"
  [endpoint client-id api-key & params]
  (let [url (url-with-params endpoint client-id api-key (into {} params))
        {:keys [status headers body error] :as resp} @(http/get url)]
    (if error
      {:error error}
      (json/parse-string body true))))

;; **************************************************************

(defmacro ->>> 
  "Safe thread macro 
   Returns err message if there is an error 
   else applies arrow threading to the response"
  [response & forms]
  `(if (contains? ~response :error)
     (->> ~response ~@forms)
     ~response))

;; Droplets
;; ****************************************
 
(defn droplets 
  "Returns all droplets"
  [client-id api-key]
  (->>> (request "droplets" client-id api-key)
        :droplets))
       
 (defn droplet 
   "Get a single droplet"
   [client-id api-key id]
   (->>>
     (request (str "droplets/" id) client-id api-key)
     :droplet))

(defn new-droplet [client-id api-key params]
  (request "droplets/new" client-id api-key
    {:name "New droplet"}))

;; Regions
;; ****************************************

(defn regions [client-id api-key]
  (->>>
    (request "regions" client-id api-key)
    :regions))
